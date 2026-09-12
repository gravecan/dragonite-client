/**
 * Dragonite License Bot - Simplified license management
 *
 * License Types:
 *   AA... = Lifetime Normal
 *   AB... = Lifetime Premium
 *   AC... = Monthly (30 days)
 *   AD... = Custom duration
 */

require('dotenv').config({ override: true });

const { Client, GatewayIntentBits, SlashCommandBuilder, EmbedBuilder, PermissionFlagsBits, AuditLogEvent, ActionRowBuilder, ButtonBuilder, ButtonStyle, ChannelType, AttachmentBuilder, ModalBuilder, TextInputBuilder, TextInputStyle } = require('discord.js');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const LICENSES_FILE = path.join(__dirname, 'licenses.json');
const BLACKLIST_FILE = path.join(__dirname, 'blacklist.json');
const EVENT_IDS_FILE = path.join(__dirname, 'event_ids.json');
const WATCHLIST_FILE = path.join(__dirname, 'watchlist.json');
const UNWATCHLIST_FILE = path.join(__dirname, 'unwatchlist.json');
const TICKETS_FILE = path.join(__dirname, 'tickets.json');
const INVITES_FILE = path.join(__dirname, 'invites.json');
const INVITED_MEMBERS_FILE = path.join(__dirname, 'invited_members.json');
const GIVEAWAYS_FILE = path.join(__dirname, 'giveaways.json');
const tickets = new Map(); // channelId -> { userId, userTag, category, createdAt }
const userInvites = new Map(); // userId -> { regular, left, fake, bonus }
const invitedMembers = new Map(); // memberId -> { inviterId, code, isFake }
const guildInvitesCache = new Map(); // guildId -> Map(code -> uses)
const giveaways = new Map(); // giveawayId -> { id, prize, description, channelId, messageId, winnersCount, reqInvites, reqTag, reqRole, endAt, ended, winners, participants }
const { normalizeBlacklistValue, applyCascadeBlacklistAdd, collectCascadeRemoveKeys } = require('./blacklist-cascade');
const licenseAdmin = require('./license-admin');

const LICENSE_META_PATH = path.join(__dirname, licenseAdmin.LICENSE_META_FILE);
let licenseMeta = licenseAdmin.loadLicenseMeta(LICENSE_META_PATH);

// Helper function to update guild invite cache
async function cacheGuildInvites(guild) {
    try {
        if (!guild || !guild.members?.me?.permissions?.has(PermissionFlagsBits.ManageGuild)) return;
        const invites = await guild.invites.fetch().catch(() => null);
        if (!invites) return;
        const codeMap = new Map();
        invites.forEach(inv => codeMap.set(inv.code, inv.uses || 0));
        guildInvitesCache.set(guild.id, codeMap);
    } catch (err) {
        console.error(`[Invites Cache Error - ${guild?.name}]:`, err.message);
    }
}

const LICENSE_LOOKUP_CHOICES = [
    { name: 'License Key', value: 'license' },
    { name: 'License ID', value: 'license_id' },
    { name: 'IP Address', value: 'ip' },
    { name: 'Minecraft Username', value: 'mc_username' },
    { name: 'Minecraft UUID', value: 'mc_uuid' },
    { name: 'Discord User ID', value: 'discord_id' },
    { name: 'Discord Username', value: 'discord_username' },
    { name: 'HWID (hash)', value: 'hwid' },
    { name: 'Windows Username', value: 'windows_name' },
    { name: 'PC Name', value: 'pc_name' }
];

const https = require('https');
const http = require('http');

const CONFIG = {
    DISCORD_TOKEN: (process.env.DISCORD_TOKEN || '').trim().replace(/^["']|["']$/g, ''),
    ADMIN_KEY: process.env.ADMIN_KEY || '',
    AUTH_SERVER_URL: process.env.AUTH_SERVER_URL || 'http://127.0.0.1:8000',
    DISCORD_WEBHOOK: (process.env.DISCORD_WEBHOOK || '').trim().replace(/^["']|["']$/g, ''),
};

function formatDisplayIp(ip) {
    if (!ip) return 'Unknown';
    return String(ip).replace(/^::ffff:/i, '');
}

function getAllowedGuildIds() {
    const rawIds = [
        process.env.GUILD_ID,
        process.env.GUILD_ID_CUSTOMERS,
        process.env.DISCORD_CUSTOMER_GUILD_ID,
        process.env.CUSTOMER_GUILD_ID
    ];
    const valid = [];
    for (const raw of rawIds) {
        if (!raw) continue;
        const cleaned = String(raw).trim().replace(/^["']|["']$/g, '');
        if (cleaned && /^\d+$/.test(cleaned) && !valid.includes(cleaned)) {
            valid.push(cleaned);
        }
    }
    return valid;
}

function getActiveCategoryCount(watchData, category = null, windowMs = 24 * 60 * 60 * 1000) {
    if (!watchData) return 0;
    if (!Array.isArray(watchData.actions)) watchData.actions = [];
    const now = Date.now();
    // Filter active actions within windowMs (24 hours)
    watchData.actions = watchData.actions.filter(a => a && a.time && (now - new Date(a.time).getTime() < windowMs));
    watchData.actionCount = watchData.actions.length;

    if (category) {
        return watchData.actions.filter(a => a.category === category).length;
    }
    return watchData.actions.length;
}

function postStartupWebhook(message) {
    const webhook = CONFIG.DISCORD_WEBHOOK;
    if (!webhook || /your_webhook/i.test(webhook) || !webhook.startsWith('https://discord.com/api/webhooks/')) {
        return;
    }
    try {
        const url = new URL(webhook);
        const data = JSON.stringify({ content: message });
        const req = https.request({
            hostname: url.hostname,
            path: url.pathname + url.search,
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) }
        }, (res) => {
            res.on('data', () => {});
            res.on('end', () => {});
        });
        req.on('error', (e) => console.error('[Bot] Startup webhook error:', e.message));
        req.write(data);
        req.end();
    } catch (e) {
        console.error('[Bot] Startup webhook error:', e.message);
    }
}

// Data storage
const licenses = new Map();
const blacklist = new Map(); // IP/HWID/License -> reason
const eventIds = new Map(); // Event ID -> { hwid, license, ip, firstSeen, blacklisted }
const watchlist = new Map(); // UserID -> { addedBy, addedAt, reason, actionCount, actions: [] }
const unwatchlist = new Set(); // UserIDs that are EXEMPTED from watching (added via /unwatch)
let watchAllMode = false; // Watch-All Mode: OFF by default (only watch users explicitly added to /watchadd)

function isWatchedUser(userId, guild) {
    if (!userId) return false;
    const ownerId = (process.env.DISCORD_OWNER_ID || guild?.ownerId || '').trim().replace(/^["']|["']$/g, '');
    if (ownerId && userId === ownerId) return false;

    // Explicitly exempted via /unwatch
    if (unwatchlist.has(userId)) return false;

    // If watchAllMode is enabled via command, watch non-owner users
    if (watchAllMode) return true;

    // Check explicit watchlist (/watchadd)
    return watchlist.has(userId);
}

function loadData() {
    try {
        if (fs.existsSync(LICENSES_FILE)) {
            const raw = fs.readFileSync(LICENSES_FILE, 'utf8');
            const parsed = JSON.parse(raw);
            for (const [k, v] of Object.entries(parsed)) licenses.set(k, normalizeLicenseRecord(v));
        }
        if (fs.existsSync(BLACKLIST_FILE)) {
            const raw = fs.readFileSync(BLACKLIST_FILE, 'utf8');
            const parsed = JSON.parse(raw);
            for (const [k, v] of Object.entries(parsed)) blacklist.set(k, v);
        }
        if (fs.existsSync(EVENT_IDS_FILE)) {
            const raw = fs.readFileSync(EVENT_IDS_FILE, 'utf8');
            const parsed = JSON.parse(raw);
            for (const [k, v] of Object.entries(parsed)) eventIds.set(k, v);
        }
        if (fs.existsSync(WATCHLIST_FILE)) {
            const raw = fs.readFileSync(WATCHLIST_FILE, 'utf8');
            const parsed = JSON.parse(raw);
            for (const [k, v] of Object.entries(parsed)) watchlist.set(k, v);
        }
        if (fs.existsSync(UNWATCHLIST_FILE)) {
            const raw = fs.readFileSync(UNWATCHLIST_FILE, 'utf8');
            const parsed = JSON.parse(raw);
            if (Array.isArray(parsed)) {
                unwatchlist.clear();
                parsed.forEach(id => unwatchlist.add(id));
            }
        }
        if (fs.existsSync(TICKETS_FILE)) {
            const raw = fs.readFileSync(TICKETS_FILE, 'utf8');
            const parsed = JSON.parse(raw);
            for (const [k, v] of Object.entries(parsed)) tickets.set(k, v);
        }
        if (fs.existsSync(INVITES_FILE)) {
            const raw = fs.readFileSync(INVITES_FILE, 'utf8');
            const parsed = JSON.parse(raw);
            for (const [k, v] of Object.entries(parsed)) userInvites.set(k, v);
        }
        if (fs.existsSync(INVITED_MEMBERS_FILE)) {
            const raw = fs.readFileSync(INVITED_MEMBERS_FILE, 'utf8');
            const parsed = JSON.parse(raw);
            for (const [k, v] of Object.entries(parsed)) invitedMembers.set(k, v);
        }
        if (fs.existsSync(GIVEAWAYS_FILE)) {
            const raw = fs.readFileSync(GIVEAWAYS_FILE, 'utf8');
            const parsed = JSON.parse(raw);
            for (const [k, v] of Object.entries(parsed)) giveaways.set(k, v);
        }
    } catch (e) {
        console.error('[Bot] Failed to load data:', e.message);
    }
}

function reloadLicensesFromDisk() {
    licenses.clear();
    if (!fs.existsSync(LICENSES_FILE)) return;
    const parsed = JSON.parse(fs.readFileSync(LICENSES_FILE, 'utf8'));
    for (const [k, v] of Object.entries(parsed)) licenses.set(k, normalizeLicenseRecord(v));
}

function reloadBlacklistFromDisk() {
    blacklist.clear();
    if (!fs.existsSync(BLACKLIST_FILE)) return;
    const parsed = JSON.parse(fs.readFileSync(BLACKLIST_FILE, 'utf8'));
    for (const [k, v] of Object.entries(parsed)) blacklist.set(k, v);
}

function reloadWatchlistFromDisk() {
    watchlist.clear();
    if (fs.existsSync(WATCHLIST_FILE)) {
        const parsed = JSON.parse(fs.readFileSync(WATCHLIST_FILE, 'utf8'));
        for (const [k, v] of Object.entries(parsed)) watchlist.set(k, v);
    }
    unwatchlist.clear();
    if (fs.existsSync(UNWATCHLIST_FILE)) {
        const parsed = JSON.parse(fs.readFileSync(UNWATCHLIST_FILE, 'utf8'));
        if (Array.isArray(parsed)) parsed.forEach(id => unwatchlist.add(id));
    }
    tickets.clear();
    if (fs.existsSync(TICKETS_FILE)) {
        const parsed = JSON.parse(fs.readFileSync(TICKETS_FILE, 'utf8'));
        for (const [k, v] of Object.entries(parsed)) tickets.set(k, v);
    }
    userInvites.clear();
    if (fs.existsSync(INVITES_FILE)) {
        const parsed = JSON.parse(fs.readFileSync(INVITES_FILE, 'utf8'));
        for (const [k, v] of Object.entries(parsed)) userInvites.set(k, v);
    }
    invitedMembers.clear();
    if (fs.existsSync(INVITED_MEMBERS_FILE)) {
        const parsed = JSON.parse(fs.readFileSync(INVITED_MEMBERS_FILE, 'utf8'));
        for (const [k, v] of Object.entries(parsed)) invitedMembers.set(k, v);
    }
    giveaways.clear();
    if (fs.existsSync(GIVEAWAYS_FILE)) {
        const parsed = JSON.parse(fs.readFileSync(GIVEAWAYS_FILE, 'utf8'));
        for (const [k, v] of Object.entries(parsed)) giveaways.set(k, v);
    }
}

function reloadAllFromDisk() {
    reloadLicensesFromDisk();
    reloadBlacklistFromDisk();
    reloadWatchlistFromDisk();
    licenseMeta = licenseAdmin.loadLicenseMeta(LICENSE_META_PATH);
    licenseAdmin.migrateLicenseIds(licenses, licenseMeta);
    licenseAdmin.saveLicenseMeta(licenseMeta, LICENSE_META_PATH);
}

function saveData() {
    try {
        const licObj = {};
        for (const [k, v] of licenses) licObj[k] = v;
        fs.writeFileSync(LICENSES_FILE, JSON.stringify(licObj, null, 2));
        
        const blObj = {};
        for (const [k, v] of blacklist) blObj[k] = v;
        fs.writeFileSync(BLACKLIST_FILE, JSON.stringify(blObj, null, 2));
        
        const evObj = {};
        for (const [k, v] of eventIds) evObj[k] = v;
        fs.writeFileSync(EVENT_IDS_FILE, JSON.stringify(evObj, null, 2));

        const watchObj = {};
        for (const [k, v] of watchlist) watchObj[k] = v;
        fs.writeFileSync(WATCHLIST_FILE, JSON.stringify(watchObj, null, 2));

        fs.writeFileSync(UNWATCHLIST_FILE, JSON.stringify(Array.from(unwatchlist), null, 2));

        const tickObj = {};
        for (const [k, v] of tickets) tickObj[k] = v;
        fs.writeFileSync(TICKETS_FILE, JSON.stringify(tickObj, null, 2));

        const invObj = {};
        for (const [k, v] of userInvites) invObj[k] = v;
        fs.writeFileSync(INVITES_FILE, JSON.stringify(invObj, null, 2));

        const memInvObj = {};
        for (const [k, v] of invitedMembers) memInvObj[k] = v;
        fs.writeFileSync(INVITED_MEMBERS_FILE, JSON.stringify(memInvObj, null, 2));

        const giveObj = {};
        for (const [k, v] of giveaways) giveObj[k] = v;
        fs.writeFileSync(GIVEAWAYS_FILE, JSON.stringify(giveObj, null, 2));
    } catch (e) {
        console.error('[Bot] Failed to save data:', e.message);
    }
}

// Create client
const client = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMessages,
        GatewayIntentBits.MessageContent,
        GatewayIntentBits.DirectMessages,
        GatewayIntentBits.GuildMembers,
        GatewayIntentBits.GuildModeration,
        GatewayIntentBits.GuildInvites
    ],
    allowedMentions: { parse: ['users', 'roles'], repliedUser: true }
});

function normalizeLicenseRecord(data) {
    if (!data || typeof data !== 'object') return data;
    if (!data.licenseType && data.type) {
        const t = String(data.type).toLowerCase();
        if (t.includes('premium')) data.licenseType = 'lifetime_premium';
        else if (t.includes('monthly')) data.licenseType = 'monthly';
        else if (t.includes('custom')) data.licenseType = 'custom';
        else data.licenseType = 'lifetime_normal';
    }
    if (data.hwidChanges == null) data.hwidChanges = 0;
    if (data.active == null) data.active = false;
    if (!Array.isArray(data.allowedJarHashes)) data.allowedJarHashes = [];
    return licenseAdmin.ensureLicenseShape(data);
}

const LICENSE_PREFIX = {
    lifetime_normal: 'AA',
    lifetime_premium: 'AB',
    monthly: 'AC',
    custom: 'AD'
};

/** Must match client DialogHandler: 20 chars → XXXX-XXXX-XXXX-XXXX-XXXX */
function generateLicenseKey(licenseType) {
    const prefix = LICENSE_PREFIX[licenseType] || 'AX';
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    let key = prefix;
    for (let i = 0; i < 18; i++) key += chars.charAt(Math.floor(Math.random() * chars.length));
    return key;
}

function formatLicenseKeyDisplay(key) {
    const raw = String(key).replace(/[^A-Za-z0-9]/g, '').toUpperCase();
    if (raw.length !== 20) return key;
    return `${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}`;
}

// Helper: Get license type info
function getLicenseTypeInfo(type) {
    const types = {
        'lifetime_normal': { name: 'Lifetime Normal', duration: null },
        'lifetime_premium': { name: 'Lifetime Premium', duration: null },
        'monthly': { name: 'Monthly', duration: 30 * 86400000 },
        'custom': { name: 'Custom', duration: null }
    };
    return types[type] || { name: 'Unknown', duration: null };
}

// Helper: Parse duration (1d, 1w, 1m, 1y)
function parseDuration(str) {
    const match = str.match(/^(\d+)([smhdwy])$/i);
    if (!match) return null;
    const num = parseInt(match[1]);
    const unit = match[2].toLowerCase();
    const multipliers = { s: 1000, m: 60000, h: 3600000, d: 86400000, w: 604800000, y: 31536000000 };
    return num * (multipliers[unit] || 86400000);
}

// Helper: Check admin permission & owner status
function isAdmin(interaction) {
    return interaction.member && interaction.member.permissions && interaction.member.permissions.has(PermissionFlagsBits.Administrator);
}

function isOwner(interaction) {
    const ownerId = (process.env.DISCORD_OWNER_ID || '').trim().replace(/^["']|["']$/g, '');
    const guildOwnerId = interaction.guild?.ownerId;
    const userId = interaction.user.id;

    if (ownerId && userId === ownerId) return true;
    if (guildOwnerId && userId === guildOwnerId) return true;
    return false;
}

function normalizeLicenseKey(key) {
    if (!key) return '';
    return String(key).replace(/[^A-Za-z0-9]/g, '').toUpperCase();
}

function resolveLicenseKey(input) {
    if (!input) return null;
    const trimmed = String(input).trim();
    if (licenses.has(trimmed)) return trimmed;
    const norm = normalizeLicenseKey(trimmed);
    for (const [stored] of licenses) {
        if (normalizeLicenseKey(stored) === norm) return stored;
    }
    return null;
}

function short(str, max = 18) {
    if (!str) return null;
    const s = String(str);
    return s.length <= max ? s : `${s.slice(0, max)}…`;
}

function formatLicenseListLine(key, data) {
    licenseAdmin.ensureLicenseShape(data);
    const display = formatLicenseKeyDisplay(key);
    const idTag = data.currentLicenseId != null ? ` · ID **#${data.currentLicenseId}**` : '';
    const idCount = licenseAdmin.allLicenseIdsForKey(data).length;
    const expired = data.expiresAt && new Date(data.expiresAt) < new Date();
    const status = blacklist.has(key) ? '🚫' : (expired ? '❌' : (data.active ? '🟢' : '✅'));
    const type = data.licenseType || data.type || '?';
    if (!data.hwid && !data.mcUsername) {
        return `${status} \`${display}\`${idTag} · ${type} · ${idCount} ID(s) · _not activated yet_`;
    }
    const mc = data.mcUsername ? `**${short(data.mcUsername, 16)}**` : '—';
    const discord = data.discordUsername
        ? `${short(data.discordUsername, 14)}`
        : (data.discordId ? `id:${short(data.discordId, 12)}` : '—');
    const ip = data.lastIp || data.ip || '—';
    const last = data.lastLogin
        ? `<t:${Math.floor(new Date(data.lastLogin).getTime() / 1000)}:R>`
        : 'never';
    return `${status} \`${display}\`${idTag} · ${type} · ${idCount} ID(s)\n└ MC: ${mc} · Discord: ${discord} · IP: \`${ip}\` · ${last}`;
}

const BLACKLIST_TYPES = {
    license: 'License Key',
    ip: 'IP Address',
    mc_username: 'Minecraft Username',
    mc_uuid: 'Minecraft UUID',
    discord_id: 'Discord User ID',
    discord_username: 'Discord Username',
    hwid: 'HWID (hash)',
    windows_name: 'Windows Username',
    pc_name: 'PC Name'
};

// Register commands
const commands = [
    new SlashCommandBuilder()
        .setName('license')
        .setDescription('License management')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
        .addSubcommand(sub => sub
            .setName('create')
            .setDescription('Create a new license key')
            .addStringOption(opt => opt.setName('type').setDescription('License type').setRequired(true)
                .addChoices(
                    { name: 'Lifetime Normal', value: 'lifetime_normal' },
                    { name: 'Lifetime Premium', value: 'lifetime_premium' },
                    { name: 'Monthly', value: 'monthly' },
                    { name: 'Custom', value: 'custom' }
                ))
            .addStringOption(opt => opt.setName('duration').setDescription('Duration (e.g. 30d) - for Custom type').setRequired(false))
        )
        .addSubcommand(sub => sub
            .setName('list')
            .setDescription('List licenses with MC name, Discord, IP')
            .addBooleanOption(opt => opt
                .setName('active_only')
                .setDescription('Only show activated / used licenses')
                .setRequired(false))
        )
        .addSubcommand(sub => {
            sub.setName('info').setDescription('Full license key details (all IDs & stats)');
            sub.addStringOption((opt) => opt.setName('type').setDescription('Lookup by').setRequired(true).addChoices(...LICENSE_LOOKUP_CHOICES));
            sub.addStringOption((opt) => opt.setName('value').setDescription('Key, ID #, MC name, IP, UUID, HWID, …').setRequired(true));
            return sub;
        })
        .addSubcommand(sub => {
            sub.setName('history').setDescription('Admin history (filter by license ID = one period only)');
            sub.addStringOption((opt) => opt.setName('type').setDescription('Lookup by').setRequired(true).addChoices(...LICENSE_LOOKUP_CHOICES));
            sub.addStringOption((opt) => opt.setName('value').setDescription('Key, ID #, MC name, IP, …').setRequired(true));
            return sub;
        })
        .addSubcommand(sub => {
            sub.setName('reset').setDescription('Clear HWID only (keep MC, Discord, IP, PC info)');
            sub.addStringOption((opt) => opt.setName('type').setDescription('Lookup by').setRequired(true).addChoices(...LICENSE_LOOKUP_CHOICES));
            sub.addStringOption((opt) => opt.setName('value').setDescription('Key, ID #, MC name, …').setRequired(true));
            return sub;
        })
        .addSubcommand(sub => sub
            .setName('hwidreset')
            .setDescription('Reset HWID only by license key (keeps MC, Discord, IP, PC info)')
            .addStringOption(opt => opt.setName('key').setDescription('License key').setRequired(true))
        )
        .addSubcommand(sub => {
            sub.setName('clear').setDescription('Clear player info; same key, new license ID');
            sub.addStringOption((opt) => opt.setName('type').setDescription('Lookup by').setRequired(true).addChoices(...LICENSE_LOOKUP_CHOICES));
            sub.addStringOption((opt) => opt.setName('value').setDescription('Key, ID #, MC name, …').setRequired(true));
            return sub;
        })
        .addSubcommand(sub => sub
            .setName('delete')
            .setDescription('Delete a license key permanently')
            .addStringOption(opt => opt.setName('key').setDescription('License key').setRequired(true))
        )
        .addSubcommand(sub => sub
            .setName('online')
            .setDescription('Show all licenses currently in-game (active sessions)')
        ),
    
    new SlashCommandBuilder()
        .setName('blacklist')
        .setDescription('Blacklist management')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
        .addSubcommand(sub => sub
            .setName('add')
            .setDescription('Blacklist person (all known IDs from license: HWID, IP, Discord, …)')
            .addStringOption(opt => opt.setName('type').setDescription('What to blacklist').setRequired(true)
                .addChoices(
                    { name: 'License Key', value: 'license' },
                    { name: 'IP Address', value: 'ip' },
                    { name: 'Minecraft Username', value: 'mc_username' },
                    { name: 'Minecraft UUID', value: 'mc_uuid' },
                    { name: 'Discord User ID', value: 'discord_id' },
                    { name: 'Discord Username', value: 'discord_username' },
                    { name: 'HWID (hash)', value: 'hwid' },
                    { name: 'Windows Username', value: 'windows_name' },
                    { name: 'PC Name', value: 'pc_name' }
                ))
            .addStringOption(opt => opt.setName('value').setDescription('The value (paste MC name, IP, license, etc.)').setRequired(true))
            .addStringOption(opt => opt.setName('reason').setDescription('Reason').setRequired(false))
        )
        .addSubcommand(sub => sub
            .setName('remove')
            .setDescription('Remove person from blacklist (all linked keys: license, HWID, IP, Discord, …)')
            .addStringOption(opt => opt.setName('type').setDescription('Identifier type').setRequired(true)
                .addChoices(
                    { name: 'License Key', value: 'license' },
                    { name: 'IP Address', value: 'ip' },
                    { name: 'Minecraft Username', value: 'mc_username' },
                    { name: 'Minecraft UUID', value: 'mc_uuid' },
                    { name: 'Discord User ID', value: 'discord_id' },
                    { name: 'Discord Username', value: 'discord_username' },
                    { name: 'HWID (hash)', value: 'hwid' },
                    { name: 'Windows Username', value: 'windows_name' },
                    { name: 'PC Name', value: 'pc_name' }
                ))
            .addStringOption(opt => opt.setName('value').setDescription('UUID, license, Discord ID, etc. — clears entire ban').setRequired(true))
        )
        .addSubcommand(sub => sub
            .setName('list')
            .setDescription('List all blacklisted items')
        ),
    
    new SlashCommandBuilder()
        .setName('stats')
        .setDescription('Show server stats')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator),

    new SlashCommandBuilder()
        .setName('watch')
        .setDescription('Lock onto a user and monitor timeouts, bans, and pings (Cap: 2 actions)')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
        .addUserOption(opt => opt.setName('user').setDescription('User to monitor').setRequired(true))
        .addStringOption(opt => opt.setName('reason').setDescription('Reason for locking target').setRequired(false)),

    new SlashCommandBuilder()
        .setName('unwatch')
        .setDescription('Remove a user from the security watchlist')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
        .addUserOption(opt => opt.setName('user').setDescription('User to stop monitoring').setRequired(true)),

    new SlashCommandBuilder()
        .setName('watchlist')
        .setDescription('View all currently monitored users and their action counters')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator),

    new SlashCommandBuilder()
        .setName('clear')
        .setDescription('Purge messages in the current channel')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
        .addIntegerOption(opt => 
            opt.setName('amount')
               .setDescription('Number of messages to delete (1-100)')
               .setRequired(false)
               .setMinValue(1)
               .setMaxValue(100)
        )
        .addUserOption(opt => 
            opt.setName('user')
               .setDescription('Filter: Only delete messages from this specific user')
               .setRequired(false)
        )
        .addBooleanOption(opt =>
            opt.setName('all')
               .setDescription('Set to true to clear all recent messages (up to 100)')
               .setRequired(false)
        ),

    new SlashCommandBuilder()
        .setName('ticketpanel')
        .setDescription('Spawn the Dragonite Support Ticket Panel')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator),

    new SlashCommandBuilder()
        .setName('invites')
        .setDescription('Check Discord invite count and stats for yourself or a user')
        .addUserOption(opt => opt.setName('user').setDescription('User to check invites for').setRequired(false)),

    new SlashCommandBuilder()
        .setName('inviteleaderboard')
        .setDescription('Show top 10 inviters in the server'),

    new SlashCommandBuilder()
        .setName('addinvites')
        .setDescription('Add bonus invites to a user')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
        .addUserOption(opt => opt.setName('user').setDescription('Target user').setRequired(true))
        .addIntegerOption(opt => opt.setName('amount').setDescription('Number of bonus invites to add').setRequired(true)),

    new SlashCommandBuilder()
        .setName('removeinvites')
        .setDescription('Remove bonus invites from a user')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
        .addUserOption(opt => opt.setName('user').setDescription('Target user').setRequired(true))
        .addIntegerOption(opt => opt.setName('amount').setDescription('Number of bonus invites to remove').setRequired(true)),

    new SlashCommandBuilder()
        .setName('resetinvites')
        .setDescription('Reset invite statistics for a user')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
        .addUserOption(opt => opt.setName('user').setDescription('Target user').setRequired(true)),

    new SlashCommandBuilder()
        .setName('gstart')
        .setDescription('Launch Pop-Up GUI window to create a new Giveaway')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator),

    new SlashCommandBuilder()
        .setName('gend')
        .setDescription('End an active giveaway early')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
        .addStringOption(opt => opt.setName('id').setDescription('Giveaway Message ID or Giveaway ID').setRequired(true)),

    new SlashCommandBuilder()
        .setName('greroll')
        .setDescription('Reroll winner(s) for a completed giveaway')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
        .addStringOption(opt => opt.setName('id').setDescription('Giveaway Message ID or Giveaway ID').setRequired(true)),

    new SlashCommandBuilder()
        .setName('download')
        .setDescription('Download Dragonite Client .jar')
        .addStringOption(opt =>
            opt.setName('version')
               .setDescription('Select Minecraft version')
               .setRequired(true)
               .addChoices(
                   { name: '1.20.1', value: '1.20.1' },
                   { name: '1.21 / 1.21.1', value: '1.21-1.21.1' },
                   { name: '1.21.4', value: '1.21.4' },
                   { name: '1.21.11', value: '1.21.11' }
               )
        )
];

// ─────────────────────────────────────────────
// GIVEAWAY SYSTEM ENGINE (MODAL GUI & REQUIREMENT VERIFICATION)
// ─────────────────────────────────────────────

function parseGiveawayDuration(str) {
    if (!str) return null;
    const match = String(str).trim().match(/^(\d+)\s*([smhdw])$/i);
    if (!match) return null;
    const num = parseInt(match[1], 10);
    const unit = match[2].toLowerCase();
    switch (unit) {
        case 's': return num * 1000;
        case 'm': return num * 60 * 1000;
        case 'h': return num * 60 * 60 * 1000;
        case 'd': return num * 24 * 60 * 60 * 1000;
        case 'w': return num * 7 * 24 * 60 * 60 * 1000;
        default: return null;
    }
}

function parseGiveawayRequirements(rawStr) {
    if (!rawStr || rawStr.trim().toLowerCase() === 'none') {
        return { reqInvites: 0, reqTag: '', reqRole: '', reqDisplay: 'None' };
    }

    let reqInvites = 0;
    let reqTag = '';
    let reqRole = '';
    const parts = [];

    const tokens = rawStr.split(/,|\n/).map(t => t.trim()).filter(Boolean);
    for (const token of tokens) {
        if (/^invites?:?\s*\d+/i.test(token)) {
            const num = parseInt(token.replace(/\D/g, ''), 10);
            if (num > 0) {
                reqInvites = num;
                parts.push(`• **${num} Invites**`);
            }
        } else if (/^tags?:?\s*\S+/i.test(token)) {
            const tagVal = token.replace(/^tags?:?\s*/i, '').trim();
            if (tagVal) {
                reqTag = tagVal;
                parts.push(`• Server Tag: **"${tagVal}"** in Username`);
            }
        } else if (/^roles?:?\s*\S+/i.test(token)) {
            const roleVal = token.replace(/^roles?:?\s*/i, '').trim();
            if (roleVal) {
                reqRole = roleVal;
                parts.push(`• Required Role: **${roleVal}**`);
            }
        }
    }

    return {
        reqInvites,
        reqTag,
        reqRole,
        reqDisplay: parts.length > 0 ? parts.join('\n') : 'None'
    };
}

function buildGiveawayEmbed(giveaway, guild) {
    const endSec = Math.floor(giveaway.endAt / 1000);
    const isEnded = giveaway.ended;

    const reqDisplay = giveaway.reqDisplay || 'None';
    const joinedCount = Array.isArray(giveaway.participants) ? giveaway.participants.length : 0;

    const embed = new EmbedBuilder()
        .setTitle(`🎉 GIVEAWAY: ${giveaway.prize}`)
        .setColor(isEnded ? 0x2F3136 : 0x5865F2)
        .setDescription(giveaway.description ? `**${giveaway.description}**\n\n` : '')
        .addFields(
            { name: '⏱️ Ends / Ended', value: isEnded ? `<t:${endSec}:F>` : `<t:${endSec}:R> (<t:${endSec}:f>)`, inline: true },
            { name: '👑 Winners', value: String(giveaway.winnersCount || 1), inline: true },
            { name: '👥 Joined', value: `**${joinedCount}** entries`, inline: true },
            { name: '📌 Entry Requirements', value: reqDisplay, inline: false }
        )
        .setFooter({ text: isEnded ? 'Giveaway Ended' : 'Click the 🎉 button below to enter!' });

    if (isEnded) {
        if (Array.isArray(giveaway.winners) && giveaway.winners.length > 0) {
            const winnerMentions = giveaway.winners.map(id => `<@${id}>`).join(', ');
            embed.addFields({ name: '🏆 Winner(s)', value: winnerMentions, inline: false });
        } else {
            embed.addFields({ name: '🏆 Winner(s)', value: 'No valid entries / No winners', inline: false });
        }
    }

    return embed;
}

function buildGiveawayRow(giveaway) {
    if (giveaway.ended) return null;
    return new ActionRowBuilder().addComponents(
        new ButtonBuilder()
            .setCustomId(`btn_gjoin_${giveaway.id}`)
            .setLabel(`🎉 Enter (${giveaway.participants ? giveaway.participants.length : 0})`)
            .setStyle(ButtonStyle.Success)
    );
}

async function endGiveaway(giveawayId, guild) {
    const giveaway = giveaways.get(giveawayId);
    if (!giveaway || giveaway.ended) return;

    giveaway.ended = true;

    const channel = guild.channels.cache.get(giveaway.channelId) || await guild.channels.fetch(giveaway.channelId).catch(() => null);
    if (!channel) {
        saveData();
        return;
    }

    const message = giveaway.messageId ? await channel.messages.fetch(giveaway.messageId).catch(() => null) : null;

    // Pick random winners from valid participants
    const participants = giveaway.participants || [];
    const winnersCount = giveaway.winnersCount || 1;
    const winners = [];

    if (participants.length > 0) {
        const shuffled = [...participants].sort(() => 0.5 - Math.random());
        winners.push(...shuffled.slice(0, Math.min(winnersCount, shuffled.length)));
    }

    giveaway.winners = winners;
    saveData();

    // Update giveaway embed message
    const embed = buildGiveawayEmbed(giveaway, guild);
    if (message) {
        await message.edit({ embeds: [embed], components: [] }).catch(() => {});
    }

    // Send announcement in channel
    if (winners.length > 0) {
        const mentions = winners.map(id => `<@${id}>`).join(', ');
        channel.send({
            content: `🎉 **GIVEAWAY ENDED** 🎉\nCongratulations ${mentions}! You won **${giveaway.prize}**! 🏆`
        }).catch(() => {});

        // DM Winners
        for (const wId of winners) {
            const u = await guild.client.users.fetch(wId).catch(() => null);
            if (u) {
                u.send({
                    content: `🏆 **Congratulations!** You won **${giveaway.prize}** in **${guild.name}**!`
                }).catch(() => {});
            }
        }
    } else {
        channel.send({
            content: `🎉 **GIVEAWAY ENDED** — No valid entries recorded for **${giveaway.prize}**.`
        }).catch(() => {});
    }
}

async function handleGiveawayJoin(interaction, giveawayId) {
    await interaction.deferReply({ ephemeral: true });

    const giveaway = giveaways.get(giveawayId);
    if (!giveaway || giveaway.ended) {
        return interaction.editReply({ content: '❌ This giveaway has already ended.' });
    }

    const userId = interaction.user.id;
    if (!Array.isArray(giveaway.participants)) giveaway.participants = [];

    // Allow leaving if already entered
    if (giveaway.participants.includes(userId)) {
        giveaway.participants = giveaway.participants.filter(id => id !== userId);
        saveData();

        // Update message count
        const channel = interaction.guild.channels.cache.get(giveaway.channelId);
        if (channel) {
            const msg = giveaway.messageId ? await channel.messages.fetch(giveaway.messageId).catch(() => null) : null;
            if (msg) {
                const embed = buildGiveawayEmbed(giveaway, interaction.guild);
                const row = buildGiveawayRow(giveaway);
                await msg.edit({ embeds: [embed], components: row ? [row] : [] }).catch(() => {});
            }
        }

        return interaction.editReply({ content: '🚪 You have left the giveaway.' });
    }

    // AUTOMATIC REQUIREMENT CHECKS
    const member = interaction.member || await interaction.guild.members.fetch(userId).catch(() => null);
    const failed = [];

    // 1. Invite Requirement Check
    if (giveaway.reqInvites > 0) {
        const data = userInvites.get(userId) || { regular: 0, left: 0, fake: 0, bonus: 0 };
        const regular = data.regular || 0;
        const left = data.left || 0;
        const fake = data.fake || 0;
        const bonus = data.bonus || 0;
        const total = Math.max(0, (regular + bonus) - (left + fake));

        if (total < giveaway.reqInvites) {
            failed.push(`• **${giveaway.reqInvites} Invites** required (You currently have **${total}**)`);
        }
    }

    // 2. Server Tag Requirement Check (Supports Official Discord Server Tag Badges + Username/Nickname Tags)
    if (giveaway.reqTag) {
        const targetTag = giveaway.reqTag.toLowerCase().replace(/^[.#]/, '');

        // Official Discord Server/Clan Tag metadata
        const officialClanTag = (member?.user?.clan?.tag || member?.user?.primaryGuild?.tag || member?.guild?.clan?.tag || '').toLowerCase();

        // User profile name fields
        const username = (interaction.user.username || '').toLowerCase();
        const globalName = (interaction.user.globalName || '').toLowerCase();
        const displayName = (member?.displayName || '').toLowerCase();
        const nickname = (member?.nickname || '').toLowerCase();

        const hasTag = (officialClanTag && officialClanTag.includes(targetTag)) ||
            username.includes(targetTag) ||
            globalName.includes(targetTag) ||
            displayName.includes(targetTag) ||
            nickname.includes(targetTag);

        if (!hasTag) {
            failed.push(`• Must have the **"${giveaway.reqTag}"** server tag badge or tag in your username/display name`);
        }
    }

    // 3. Role Requirement Check
    if (giveaway.reqRole) {
        const hasRole = member?.roles?.cache?.some(r => r.id === giveaway.reqRole || r.name.toLowerCase() === giveaway.reqRole.toLowerCase());
        if (!hasRole) {
            failed.push(`• Must have the required role (**${giveaway.reqRole}**)`);
        }
    }

    if (failed.length > 0) {
        return interaction.editReply({
            content: `❌ **Entry Denied**: You do not meet the giveaway requirements:\n\n${failed.join('\n')}`
        });
    }

    // Add to participants
    giveaway.participants.push(userId);
    saveData();

    // Update message button count
    const channel = interaction.guild.channels.cache.get(giveaway.channelId);
    if (channel) {
        const msg = giveaway.messageId ? await channel.messages.fetch(giveaway.messageId).catch(() => null) : null;
        if (msg) {
            const embed = buildGiveawayEmbed(giveaway, interaction.guild);
            const row = buildGiveawayRow(giveaway);
            await msg.edit({ embeds: [embed], components: row ? [row] : [] }).catch(() => {});
        }
    }

    return interaction.editReply({
        content: `🎉 **Successfully Entered!** Good luck in the giveaway!`
    });
}

async function generateHtmlTranscript(channel, ticketData) {
    const fetchedMessages = await channel.messages.fetch({ limit: 100 }).catch(() => null);
    if (!fetchedMessages) return null;

    const messagesList = Array.from(fetchedMessages.values()).reverse();
    const ticketId = channel.name;
    const filename = `${ticketId}.html`;
    const transcriptsFolder = path.join(__dirname, 'transcripts');
    const primaryFilePath = path.join(transcriptsFolder, filename);

    let rowsHtml = '';
    for (const msg of messagesList) {
        console.log(`[Transcript Debug] msg ${msg.id}: attachments=${msg.attachments?.size || 0}, stickers=${msg.stickers?.size || 0}, embeds=${msg.embeds?.length || 0}`);
        if (msg.stickers?.size) {
            for (const [, st] of msg.stickers) console.log(`  sticker: id=${st.id} name=${st.name} format=${st.format}`);
        }
        if (msg.attachments?.size) {
            for (const [, att] of msg.attachments) console.log(`  attachment: name=${att.name} contentType=${att.contentType} url=${att.url?.slice(0, 80)}`);
        }

        const timeStr = new Date(msg.createdTimestamp).toLocaleString();
        const avatarUrl = msg.author.displayAvatarURL({ size: 64, extension: 'png' });

        let contentHtml = (msg.content || '');

        // 1. Escape HTML
        contentHtml = contentHtml
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');

        // 2. User Mentions (<@id> & <@!id>)
        contentHtml = contentHtml.replace(/&lt;@!?(\d+)&gt;/g, (match, id) => {
            const member = channel.guild ? channel.guild.members.cache.get(id) : null;
            const name = member ? member.displayName : id;
            return `<span class="mention">@${name}</span>`;
        });

        // 3. Embedded GIFs and Image URLs in content text (MUST RUN BEFORE EMOJI <img> TAG INSERTION!)
        const imgUrlRegex = /(https?:\/\/\S+\.(?:png|jpg|jpeg|gif|webp)(?:\?\S+)?)/gi;
        contentHtml = contentHtml.replace(imgUrlRegex, (url) => {
            return `<br><a href="${url}" target="_blank"><img class="attached-img" src="${url}" alt="Image" loading="lazy" onerror="this.onerror=null;this.parentElement.innerHTML='🔗 Link: ${url}';"></a><br>`;
        });

        // 4. Custom Discord Emojis (<:name:id> & <a:name:id>) -> Insert <img> tags AFTER url regex
        contentHtml = contentHtml.replace(/&lt;a?:([a-zA-Z0-9_]+):(\d+)&gt;/g, (match, name, id) => {
            const ext = match.includes('&lt;a:') ? 'gif' : 'png';
            return `<img class="disc-emoji" src="https://cdn.discordapp.com/emojis/${id}.${ext}" alt="${name}" title="${name}">`;
        });

        // 5. Linebreaks
        contentHtml = contentHtml.replace(/\n/g, '<br>');

        // 6. Stickers
        let stickersHtml = '';
        if (msg.stickers && msg.stickers.size > 0) {
            for (const [, st] of msg.stickers) {
                if (st.format === 3) continue; // Skip Lottie format
                const ext = st.format === 4 ? 'gif' : 'png'; // Format 4 = GIF, Format 1 & 2 (APNG) = PNG
                const stUrl = `https://cdn.discordapp.com/stickers/${st.id}.${ext}`;
                stickersHtml += `<br><img class="attached-sticker" src="${stUrl}" alt="${st.name}" title="${st.name}" loading="lazy">`;
            }
        }

        // 7. Message Attachments (Images, Videos, Files)
        let attachmentsHtml = '';
        if (msg.attachments && msg.attachments.size > 0) {
            for (const [, att] of msg.attachments) {
                const attUrl = att.url || att.proxyURL;
                const contentType = att.contentType || '';
                const isImage = contentType.startsWith('image/') || /\.(png|jpg|jpeg|gif|webp)$/i.test(att.name || '');
                const isVideo = contentType.startsWith('video/') || /\.(mp4|webm|mov|mkv)$/i.test(att.name || '');

                if (isImage) {
                    attachmentsHtml += `<br><a href="${attUrl}" target="_blank"><img class="attached-img" src="${attUrl}" alt="Attachment" loading="lazy"></a>`;
                } else if (isVideo) {
                    attachmentsHtml += `<br><video class="attached-video" src="${attUrl}" controls loop muted playsinline preload="metadata"></video>`;
                } else {
                    const sizeKb = Math.round((att.size || 0) / 1024);
                    attachmentsHtml += `<br><a class="attached-file" href="${attUrl}" target="_blank" download>📎 Download File: ${att.name || 'Attachment'} (${sizeKb} KB)</a>`;
                }
            }
        }

        // 8. Discord Embeds (GIFs from picker, Tenor/Giphy, videos, image embeds, link previews)
        let embedsHtml = '';
        if (msg.embeds && msg.embeds.length > 0) {
            for (const embed of msg.embeds) {
                if (embed.video && embed.video.url) {
                    embedsHtml += `<br><video class="attached-video" src="${embed.video.url}" poster="${embed.thumbnail?.url || ''}" controls loop muted playsinline preload="metadata"></video>`;
                } else if (embed.image && embed.image.url) {
                    embedsHtml += `<br><a href="${embed.image.url}" target="_blank"><img class="attached-img" src="${embed.image.url}" alt="Embedded image" loading="lazy"></a>`;
                } else if (embed.thumbnail && embed.thumbnail.url && (embed.type === 'gifv' || !embed.title)) {
                    // Fallback for Tenor/Giphy gifv embeds where video block hasn't finished populating
                    embedsHtml += `<br><a href="${embed.url || embed.thumbnail.url}" target="_blank"><img class="attached-img" src="${embed.thumbnail.url}" alt="GIF" loading="lazy"></a>`;
                } else if (embed.thumbnail && embed.thumbnail.url && (embed.title || embed.description)) {
                    const title = (embed.title || 'Link Preview').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                    const desc = (embed.description || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                    embedsHtml += `<br><div class="embed-card">
                        <img class="embed-thumb" src="${embed.thumbnail.url}" alt="thumbnail" loading="lazy">
                        <div class="embed-body">
                            ${embed.url ? `<a href="${embed.url}" target="_blank" class="embed-title">${title}</a>` : `<span class="embed-title">${title}</span>`}
                            <div class="embed-desc">${desc.slice(0, 200)}</div>
                        </div>
                    </div>`;
                }
            }
        }

        rowsHtml += `
        <div class="message">
            <img class="avatar" src="${avatarUrl}" alt="avatar" loading="lazy">
            <div class="msg-body">
                <div class="msg-header">
                    <span class="username">${msg.author.tag}</span>
                    <span class="timestamp">${timeStr}</span>
                </div>
                <div class="content">${contentHtml || ''}${stickersHtml}${attachmentsHtml}${embedsHtml}</div>
            </div>
        </div>`;
    }

    const htmlContent = `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Transcript - ${ticketId}</title>
    <style>
        body { background-color: #313338; color: #dbdee1; font-family: 'gg sans', 'Noto Sans', Helvetica, Arial, sans-serif; margin: 0; padding: 20px; }
        .header { background: #2b2d31; padding: 20px; border-radius: 8px; margin-bottom: 20px; border-left: 5px solid #5865f2; }
        .header h1 { margin: 0 0 8px 0; color: #f2f3f5; font-size: 22px; }
        .header p { margin: 4px 0; color: #b5bac1; font-size: 14px; }
        .message { display: flex; margin-bottom: 12px; padding: 6px 12px; border-radius: 4px; }
        .message:hover { background-color: #2e3035; }
        .avatar { width: 40px; height: 40px; border-radius: 50%; margin-right: 16px; flex-shrink: 0; }
        .msg-body { flex: 1; }
        .msg-header { margin-bottom: 4px; display: flex; align-items: baseline; }
        .username { font-weight: 600; color: #f2f3f5; margin-right: 8px; font-size: 15px; }
        .timestamp { font-size: 12px; color: #949ba4; }
        .content { color: #dbdee1; line-height: 1.375; word-break: break-word; font-size: 14px; }
        .disc-emoji { width: 22px; height: 22px; vertical-align: middle; margin: 0 2px; }
        .attached-sticker { width: 120px; height: 120px; margin-top: 6px; display: block; border-radius: 8px; }
        .mention { background: rgba(88, 101, 242, 0.3); color: #c9cdfb; padding: 2px 4px; border-radius: 3px; font-weight: 500; }
        .attached-img { max-width: 420px; max-height: 320px; border-radius: 8px; margin-top: 8px; display: block; object-fit: contain; }
        .attached-video { max-width: 420px; max-height: 320px; border-radius: 8px; margin-top: 8px; display: block; background: #000; }
        .attached-file { color: #00a8fc; background: #2b2d31; border: 1px solid #3f4248; padding: 8px 12px; border-radius: 6px; text-decoration: none; display: inline-block; margin-top: 6px; font-size: 13px; font-weight: 500; }
        .attached-file:hover { background: #35373c; border-color: #5865f2; text-decoration: underline; }
        .embed-card { display: flex; background: #2b2d31; border-left: 4px solid #5865f2; border-radius: 4px; padding: 10px; margin-top: 8px; max-width: 420px; }
        .embed-thumb { width: 70px; height: 70px; object-fit: cover; border-radius: 4px; margin-right: 12px; }
        .embed-body { flex: 1; overflow: hidden; }
        .embed-title { color: #00a8fc; font-weight: 600; font-size: 14px; text-decoration: none; display: block; margin-bottom: 4px; }
        .embed-desc { color: #b5bac1; font-size: 12px; line-height: 1.3; }
    </style>
</head>
<body>
    <div class="header">
        <h1>Dragonite Support Transcript — #${ticketId}</h1>
        <p><strong>Category:</strong> ${ticketData?.category || 'General Support'}</p>
        <p><strong>Opened By:</strong> ${ticketData?.userTag || 'Unknown'}</p>
        <p><strong>Generated At:</strong> ${new Date().toLocaleString()}</p>
    </div>
    <div class="messages">
        ${rowsHtml}
    </div>
</body>
</html>`;

    try {
        const targetDirs = [
            path.join(__dirname, 'public', 'transcripts'),
            path.join(__dirname, 'transcripts'),
            '/root/dragonite-website/public/transcripts',
            '/root/dragonite-website/dist/transcripts',
            '/root/dragonite-website/build/transcripts'
        ];

        for (const dir of targetDirs) {
            try {
                fs.mkdirSync(dir, { recursive: true });
                fs.writeFileSync(path.join(dir, filename), htmlContent, 'utf8');
            } catch (e) {}
        }

        return {
            webUrl: `https://dragoniteclient.fun/transcripts/${filename}`,
            filePath: primaryFilePath,
            fileName: filename
        };
    } catch (err) {
        console.error('[Transcript Error]:', err.message);
        return null;
    }
}

async function handleTicketButton(interaction) {
    const customId = interaction.customId;

    if (['btn_ticket_purchase', 'btn_ticket_bug', 'btn_ticket_general'].includes(customId)) {
        await interaction.deferReply({ ephemeral: true });

        const catMap = {
            btn_ticket_purchase: 'Purchase / License',
            btn_ticket_bug: 'Report Bug',
            btn_ticket_general: 'General Help'
        };
        const categoryName = catMap[customId];

        // Check open tickets for this user (Max 2)
        const userOpenTickets = [];
        for (const [chanId, tData] of tickets.entries()) {
            if (tData.userId === interaction.user.id) {
                const chan = interaction.guild.channels.cache.get(chanId);
                if (chan) {
                    userOpenTickets.push(chan);
                } else {
                    tickets.delete(chanId);
                }
            }
        }

        if (userOpenTickets.length >= 2) {
            const chanLinks = userOpenTickets.map(c => `<#${c.id}>`).join(', ');
            return interaction.editReply({
                content: `❌ You already have **2 open tickets** (${chanLinks}). Please close an existing ticket before opening a new one.`
            });
        }

        const baseUsername = interaction.user.username.toLowerCase().replace(/[^a-z0-9]/g, '');
        const ticketSuffix = userOpenTickets.length > 0 ? `-${userOpenTickets.length + 1}` : '';
        const ticketName = `ticket-${baseUsername}${ticketSuffix}`.slice(0, 25);

        const ticketRoomChannel = interaction.guild.channels.cache.get('1166831465464606841');
        const parentCategoryId = ticketRoomChannel ? ticketRoomChannel.parentId : null;

        const ticketChannel = await interaction.guild.channels.create({
            name: ticketName,
            type: ChannelType.GuildText,
            parent: parentCategoryId || undefined,
            permissionOverwrites: [
                { id: interaction.guild.roles.everyone.id, deny: [PermissionFlagsBits.ViewChannel] },
                { id: interaction.user.id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.AttachFiles, PermissionFlagsBits.ReadMessageHistory] },
                { id: interaction.client.user.id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ManageChannels] }
            ]
        }).catch(err => {
            console.error('[Ticket Create Error]:', err.message);
            return null;
        });

        if (!ticketChannel) {
            return interaction.editReply({ content: '❌ Failed to create ticket channel. Check bot permissions.' });
        }

        tickets.set(ticketChannel.id, {
            userId: interaction.user.id,
            userTag: interaction.user.tag,
            category: categoryName,
            createdAt: new Date().toISOString()
        });
        saveData();

        const embed = new EmbedBuilder()
            .setTitle(`Support Ticket — ${categoryName}`)
            .setColor(0x5865F2)
            .setDescription(`Yo what's up <@${interaction.user.id}>!\nStaff will be with you shortly. Please describe your issue in detail.`)
            .addFields(
                { name: 'Category', value: categoryName, inline: true },
                { name: 'Opened By', value: `<@${interaction.user.id}>`, inline: true }
            );

        const row = new ActionRowBuilder().addComponents(
            new ButtonBuilder().setCustomId('btn_ticket_close').setLabel('Close Ticket').setStyle(ButtonStyle.Danger).setEmoji('🔒'),
            new ButtonBuilder().setCustomId('btn_ticket_transcript').setLabel('Web Transcript').setStyle(ButtonStyle.Secondary).setEmoji('🌐')
        );

        await ticketChannel.send({ content: `<@${interaction.user.id}>`, embeds: [embed], components: [row] });
        await interaction.editReply({ content: `✅ Ticket created! Head over to <#${ticketChannel.id}>.` });
    }

    else if (customId === 'btn_ticket_close' || customId === 'btn_ticket_transcript') {
        await interaction.deferReply({ ephemeral: true });

        const ticketData = tickets.get(interaction.channel.id) || { userTag: interaction.user.tag, category: 'Support Ticket', userId: interaction.user.id };
        const transcriptRes = await generateHtmlTranscript(interaction.channel, ticketData);
        const webUrl = transcriptRes?.webUrl;
        const filePath = transcriptRes?.filePath;

        if (customId === 'btn_ticket_transcript') {
            const attachment = filePath && fs.existsSync(filePath) ? new AttachmentBuilder(filePath, { name: `${interaction.channel.name}.html` }) : null;
            return interaction.editReply({
                content: `📄 **Ticket Transcript Generated**:`,
                files: attachment ? [attachment] : []
            });
        }

        await interaction.editReply({ content: '🔒 Closing ticket and generating transcript...' });

        const logChannelId = '1531585226332049449';
        const logChannel = interaction.guild.channels.cache.get(logChannelId) || await interaction.guild.channels.fetch(logChannelId).catch(() => null);

        const fileAttachment1 = filePath && fs.existsSync(filePath) ? new AttachmentBuilder(filePath, { name: `${interaction.channel.name}.html` }) : null;

        if (logChannel && logChannel.isTextBased()) {
            const logEmbed = new EmbedBuilder()
                .setTitle(`📄 Ticket Closed: ${interaction.channel.name}`)
                .setColor(0xFF0000)
                .addFields(
                    { name: 'Category', value: ticketData.category || 'General', inline: true },
                    { name: 'Opened By', value: `<@${ticketData.userId}> (${ticketData.userTag})`, inline: true },
                    { name: 'Closed By', value: `<@${interaction.user.id}>`, inline: true }
                )
                .setFooter({ text: 'Dragonite Client Ticket System' });

            await logChannel.send({
                embeds: [logEmbed],
                files: fileAttachment1 ? [fileAttachment1] : []
            }).catch(() => {});
        }

        const ticketUser = await interaction.client.users.fetch(ticketData.userId).catch(() => null);
        if (ticketUser) {
            const fileAttachment2 = filePath && fs.existsSync(filePath) ? new AttachmentBuilder(filePath, { name: `${interaction.channel.name}.html` }) : null;
            await ticketUser.send({
                content: `📄 Your ticket **${interaction.channel.name}** has been closed. Your transcript file is attached below.`,
                files: fileAttachment2 ? [fileAttachment2] : []
            }).catch(() => {});
        }

        tickets.delete(interaction.channel.id);
        saveData();

        setTimeout(() => {
            interaction.channel.delete('Ticket closed').catch(() => {});
        }, 3000);
    }
}

// Handle interactions
client.on('interactionCreate', async (interaction) => {
    if (interaction.isButton()) {
        if (interaction.customId.startsWith('btn_gjoin_')) {
            const giveawayId = interaction.customId.replace('btn_gjoin_', '');
            await handleGiveawayJoin(interaction, giveawayId);
            return;
        }
        await handleTicketButton(interaction);
        return;
    }

    if (interaction.isModalSubmit() && interaction.customId === 'modal_giveaway_create') {
        await interaction.deferReply({ ephemeral: true });

        const prize = interaction.fields.getTextInputValue('g_prize');
        const rawDuration = interaction.fields.getTextInputValue('g_duration');
        const rawWinners = interaction.fields.getTextInputValue('g_winners');
        const rawReqs = interaction.fields.getTextInputValue('g_reqs');
        const description = interaction.fields.getTextInputValue('g_desc') || '';

        const durationMs = parseGiveawayDuration(rawDuration);
        if (!durationMs) {
            return interaction.editReply({
                content: `❌ Invalid duration format \`${rawDuration}\`. Use formats like \`10m\`, \`1h\`, \`2d\`, or \`1w\`.`
            });
        }

        const winnersCount = Math.max(1, parseInt(rawWinners, 10) || 1);
        const reqObj = parseGiveawayRequirements(rawReqs);

        const endAt = Date.now() + durationMs;
        const giveawayId = `g_${Date.now()}`;

        const giveaway = {
            id: giveawayId,
            prize,
            description,
            channelId: interaction.channel.id,
            messageId: null,
            winnersCount,
            reqInvites: reqObj.reqInvites,
            reqTag: reqObj.reqTag,
            reqRole: reqObj.reqRole,
            reqDisplay: reqObj.reqDisplay,
            endAt,
            ended: false,
            winners: [],
            participants: []
        };

        const embed = buildGiveawayEmbed(giveaway, interaction.guild);
        const row = buildGiveawayRow(giveaway);

        const msg = await interaction.channel.send({ embeds: [embed], components: row ? [row] : [] });
        giveaway.messageId = msg.id;

        giveaways.set(giveawayId, giveaway);
        saveData();

        setTimeout(() => {
            endGiveaway(giveawayId, interaction.guild);
        }, durationMs);

        await interaction.editReply({ content: `🎉 Giveaway for **${prize}** posted successfully!` });
        return;
    }

    if (!interaction.isChatInputCommand()) return;
    
    // SECURITY LOCKDOWN: Restrict admin commands to Server/Bot Owner while allowing public/customer commands
    const publicCustomerCmds = ['invites', 'inviteleaderboard', 'download'];
    if (!publicCustomerCmds.includes(interaction.commandName) && !isOwner(interaction)) {
        console.warn(`[SECURITY LOCKDOWN] Unauthorized user ${interaction.user.tag} (${interaction.user.id}) attempted to execute command /${interaction.commandName}`);
        return interaction.reply({
            content: '❌ **PERMISSION DENIED**: Admin commands are strictly restricted to the Server Owner / Bot Owner.',
            ephemeral: true
        });
    }
    
    const { commandName, options } = interaction;
    
    try {
        if (['license', 'blacklist', 'stats', 'watch', 'unwatch', 'watchlist'].includes(commandName)) {
            reloadAllFromDisk();
        }

        if (commandName === 'license') {
            await interaction.deferReply({ ephemeral: true });
            const sub = options.getSubcommand();

            if (sub === 'create') {
                const type = options.getString('type');
                const duration = options.getString('duration');
                
                const licenseKey = generateLicenseKey(type);
                const typeInfo = getLicenseTypeInfo(type);

                let expiresAt = null;
                if (type === 'custom' && duration) {
                    const ms = parseDuration(duration);
                    if (!ms) {
                        return interaction.editReply({ content: '❌ Invalid duration format. Use: 30d, 1w, 1m' });
                    }
                    expiresAt = new Date(Date.now() + ms).toISOString();
                } else if (typeInfo.duration) {
                    expiresAt = new Date(Date.now() + typeInfo.duration).toISOString();
                }

                const record = normalizeLicenseRecord({
                    username: null,
                    licenseType: type,
                    hwid: null,
                    hwidChanges: 0,
                    expiresAt,
                    active: false,
                    allowedJarHashes: [],
                    lastLogin: null,
                    createdAt: new Date().toISOString()
                });
                licenseAdmin.onLicenseCreated(record, licenseMeta);
                licenses.set(licenseKey, record);
                licenseAdmin.saveLicenseMeta(licenseMeta, LICENSE_META_PATH);
                saveData();

                const displayKey = formatLicenseKeyDisplay(licenseKey);
                const embed = new EmbedBuilder()
                    .setTitle('✅ License Created')
                    .setColor(0x00ff00)
                    .addFields(
                        { name: 'License Key', value: `\`${displayKey}\``, inline: true },
                        { name: 'License ID', value: `#${record.currentLicenseId}`, inline: true },
                        { name: 'Type', value: typeInfo.name, inline: true },
                        { name: 'Expires', value: expiresAt ? `<t:${Math.floor(new Date(expiresAt).getTime() / 1000)}:R>` : 'Never', inline: true }
                    )
                    .setFooter({ text: 'Dragonite License System' });

                await interaction.editReply({ embeds: [embed] });
            }

            else if (sub === 'list') {
                const activeOnly = options.getBoolean('active_only') === true;
                const rows = [];
                for (const [key, data] of licenses) {
                    if (activeOnly && !data.hwid && !data.lastLogin) continue;
                    rows.push(formatLicenseListLine(key, data));
                }

                if (rows.length === 0) {
                    return interaction.editReply({ content: 'No licenses found.' });
                }

                const stats = licenseAdmin.computeListStats(licenses);
                const legend = '🟢 in-game now · ✅ activated · ❌ expired · 🚫 blacklisted · _not activated yet_';
                const embed = new EmbedBuilder()
                    .setTitle('📋 License List')
                    .setColor(0x5865F2)
                    .setDescription(`${legend}\n\n${rows.slice(0, 12).join('\n\n')}`)
                    .setFooter({
                        text: `Keys: ${stats.keys} (${stats.online} online, ${stats.activated} activated) · License IDs: ${stats.totalIds} total · showing ${Math.min(12, rows.length)}/${rows.length}`
                    });

                await interaction.editReply({ embeds: [embed] });
            }

            else if (sub === 'info' || sub === 'history') {
                const type = options.getString('type');
                const value = options.getString('value');
                const found = licenseAdmin.findLicenseByQuery(type, value, licenses);

                if (!found) {
                    return interaction.editReply({ content: '❌ Not found for that type + value.' });
                }

                const { key: foundKey, data, filterLicenseId, archivedOnly } = found;
                const expired = data.expiresAt && new Date(data.expiresAt) < new Date();
                const bl = blacklist.has(foundKey) || (data.hwid && blacklist.has(data.hwid))
                    || (data.ip && blacklist.has(data.ip))
                    || (data.mcUsername && blacklist.has(data.mcUsername.toLowerCase()));

                if (sub === 'history') {
                    const filterNote = filterLicenseId != null
                        ? `Showing events for license ID **#${filterLicenseId}** only`
                        : 'Showing **all** events for this license key';
                    const embed = new EmbedBuilder()
                        .setTitle('📜 License History')
                        .setColor(0x5865F2)
                        .setDescription(filterNote)
                        .addFields(
                            { name: 'License Key', value: `\`${formatLicenseKeyDisplay(foundKey)}\``, inline: true },
                            { name: 'Current ID', value: data.currentLicenseId != null ? `#${data.currentLicenseId}` : '—', inline: true },
                            { name: 'HWID resets', value: String(data.stats.hwidResets || 0), inline: true },
                            { name: 'Clears', value: String(data.stats.clears || 0), inline: true },
                            { name: 'All IDs on key', value: licenseAdmin.allLicenseIdsForKey(data).map((id) => `#${id}`).join(', ') || '—', inline: false },
                            { name: 'Events', value: licenseAdmin.formatHistoryLines(data, filterLicenseId).slice(0, 3900), inline: false }
                        );
                    if (archivedOnly) {
                        embed.setFooter({ text: 'Archived ID — snapshot is in licenseIdHistory on server' });
                    }
                    return interaction.editReply({ embeds: [embed] });
                }

                const embed = new EmbedBuilder()
                    .setTitle('📋 License Info')
                    .setColor(bl ? 0xff0000 : (expired ? 0xffaa00 : 0x00ff00))
                    .addFields(
                        { name: 'License Key', value: `\`${formatLicenseKeyDisplay(foundKey)}\``, inline: false },
                        { name: 'Current License ID', value: data.currentLicenseId != null ? `#${data.currentLicenseId}` : '—', inline: true },
                        { name: 'All IDs', value: licenseAdmin.allLicenseIdsForKey(data).map((id) => `#${id}`).join(', ') || '—', inline: true },
                        { name: 'HWID resets / Clears', value: `${data.stats.hwidResets || 0} / ${data.stats.clears || 0}`, inline: true },
                        { name: 'Type', value: data.licenseType || data.type || 'unknown', inline: true },
                        { name: 'Status', value: bl ? '🚫 Blacklisted' : (expired ? '❌ Expired' : (data.active ? '🟢 Online' : '✅ Valid')), inline: true },
                        { name: 'Expires', value: data.expiresAt ? `<t:${Math.floor(new Date(data.expiresAt).getTime() / 1000)}:R>` : 'Never', inline: true },
                        { name: 'Minecraft', value: data.mcUsername ? `**${data.mcUsername}**\n\`${data.mcUuid || 'no uuid'}\`` : 'Not bound', inline: true },
                        { name: 'Discord', value: data.discordUsername
                            ? `**${data.discordUsername}**\n\`${data.discordId || 'no id'}\``
                            : (data.discordId ? `\`${data.discordId}\`` : 'Not linked'), inline: true },
                        { name: 'IP (last)', value: formatDisplayIp(data.lastIp || data.ip), inline: true },
                        { name: 'OS', value: data.osVersion || 'Unknown', inline: true },
                        { name: 'PC', value: `${data.windowsName || '?'}\n${data.pcName || '?'}`, inline: true },
                        { name: 'HWID (full)', value: data.hwid ? `\`\`\`${data.hwid}\`\`\`` : 'Not bound', inline: false },
                        { name: 'Logins', value: String(data.loginCount || 0), inline: true },
                        { name: 'Last login', value: data.lastLogin ? `<t:${Math.floor(new Date(data.lastLogin).getTime() / 1000)}:F>` : 'Never', inline: true },
                        { name: 'Last logout', value: data.lastLogout
                            ? `<t:${Math.floor(new Date(data.lastLogout).getTime() / 1000)}:F> (${data.lastSessionDurationSeconds || '?'}s)`
                            : 'Never', inline: true },
                        { name: 'Created', value: data.createdAt ? `<t:${Math.floor(new Date(data.createdAt).getTime() / 1000)}:R>` : '?', inline: true }
                    );

                await interaction.editReply({ embeds: [embed] });
            }

            else if (sub === 'reset' || sub === 'hwidreset') {
                let key;
                let data;

                if (sub === 'hwidreset') {
                    key = resolveLicenseKey(options.getString('key'));
                    if (!key || !licenses.has(key)) {
                        return interaction.editReply({ content: '❌ License not found for that key.' });
                    }
                    data = licenses.get(key);
                } else {
                    const type = options.getString('type');
                    const value = options.getString('value');
                    const found = licenseAdmin.findLicenseByQuery(type, value, licenses);
                    if (!found) {
                        return interaction.editReply({ content: '❌ License not found.' });
                    }
                    key = found.key;
                    data = found.data;
                }

                const lid = licenseAdmin.resetHwidOnly(data);
                saveData();

                await interaction.editReply({
                    content: `✅ HWID + MachineGuid cleared for \`${formatLicenseKeyDisplay(key)}\` (ID #${lid}). MC, Discord, IP, PC info **kept**.`
                });
            }

            else if (sub === 'clear') {
                const type = options.getString('type');
                const value = options.getString('value');
                const found = licenseAdmin.findLicenseByQuery(type, value, licenses);

                if (!found) {
                    return interaction.editReply({ content: '❌ License not found.' });
                }

                const { key, data } = found;
                const { oldId, newId } = licenseAdmin.clearLicenseIdentity(data, licenseMeta);
                licenseAdmin.saveLicenseMeta(licenseMeta, LICENSE_META_PATH);
                saveData();

                await interaction.editReply({
                    content: `✅ Cleared player info for \`${formatLicenseKeyDisplay(key)}\`. License key unchanged.\n`
                        + `License ID **#${oldId ?? '?'}** → **#${newId}** (old period saved in history).`
                });
            }

            else if (sub === 'delete') {
                const key = resolveLicenseKey(options.getString('key'));
                if (!key || !licenses.has(key)) {
                    return interaction.editReply({ content: '❌ License not found for that key.' });
                }

                licenses.delete(key);
                saveData();

                await interaction.editReply({ content: `✅ License \`${formatLicenseKeyDisplay(key)}\` deleted.` });
            }

            else if (sub === 'online') {
                const onlineList = [];
                for (const [key, data] of licenses) {
                    if (data.active === true) {
                        onlineList.push({ key, data });
                    }
                }

                if (onlineList.length === 0) {
                    return interaction.editReply({ content: '📭 No licenses currently in-game.' });
                }

                const rows = onlineList.map(({ key, data }) => {
                    const display = formatLicenseKeyDisplay(key);
                    const mc = data.mcUsername ? `**${short(data.mcUsername, 16)}**` : '—';
                    const discord = data.discordUsername
                        ? short(data.discordUsername, 14)
                        : (data.discordId ? `id:${short(data.discordId, 12)}` : '—');
                    const type = data.licenseType || '?';
                    const since = data.lastLogin
                        ? `<t:${Math.floor(new Date(data.lastLogin).getTime() / 1000)}:R>`
                        : 'unknown';
                    return `🟢 \`${display}\` · ${type}\n└ MC: ${mc} · Discord: ${discord} · since ${since}`;
                });

                const embed = new EmbedBuilder()
                    .setTitle(`🟢 Online Licenses (${onlineList.length})`)
                    .setColor(0x00ff00)
                    .setDescription(rows.slice(0, 20).join('\n\n') || '—')
                    .setFooter({ text: `${onlineList.length} online · showing ${Math.min(20, rows.length)}/${rows.length}` });

                await interaction.editReply({ embeds: [embed] });
            }
        }
        
        else if (commandName === 'blacklist') {
            await interaction.deferReply({ ephemeral: true });
            const sub = options.getSubcommand();

            if (sub === 'add') {
                const type = options.getString('type');
                const rawValue = options.getString('value');
                const reason = options.getString('reason') || 'No reason provided';

                if (!rawValue || !String(rawValue).trim()) {
                    return interaction.editReply({ content: '❌ Empty value.' });
                }

                const { keys, added } = applyCascadeBlacklistAdd(type, rawValue, licenses, blacklist, {
                    reason,
                    typeLabel: BLACKLIST_TYPES[type] || type,
                    rawValue
                });

                if (added === 0) {
                    return interaction.editReply({ content: '❌ Could not resolve any blacklist keys.' });
                }

                saveData();

                const preview = keys.slice(0, 10).map((k) => `\`${k}\``).join('\n');
                const more = keys.length > 10 ? `\n… and ${keys.length - 10} more` : '';
                await interaction.editReply({
                    content: `✅ Blacklisted **${added}** keys for this person (${BLACKLIST_TYPES[type]})\nReason: ${reason}\n${preview}${more}`
                });
            }

            else if (sub === 'remove') {
                const type = options.getString('type');
                const rawValue = options.getString('value');

                const keys = collectCascadeRemoveKeys(type, rawValue, licenses, blacklist);
                if (keys.length === 0) {
                    return interaction.editReply({
                        content: '❌ No matching blacklist entries (check type + value).'
                    });
                }

                for (const key of keys) {
                    blacklist.delete(key);
                }
                saveData();

                const preview = keys.slice(0, 12).map((k) => `\`${k}\``).join('\n');
                const more = keys.length > 12 ? `\n… and ${keys.length - 12} more` : '';
                await interaction.editReply({
                    content: `✅ Removed **${keys.length}** blacklist entries for this subject (${BLACKLIST_TYPES[type]}):\n${preview}${more}`
                });
            }

            else if (sub === 'list') {
                const list = [];
                for (const [target, data] of blacklist) {
                    const label = data.typeLabel || data.type || 'entry';
                    list.push(`**${label}** · \`${target}\` · ${data.reason || '—'}`);
                }

                if (list.length === 0) {
                    return interaction.editReply({ content: 'Blacklist is empty.' });
                }

                await interaction.editReply({ content: `**Blacklisted:**\n${list.slice(0, 20).join('\n')}` });
            }
        }
        
        else if (commandName === 'stats') {
            const totalLicenses = licenses.size;
            const activeLicenses = [...licenses.values()].filter(l => !l.expiresAt || new Date(l.expiresAt) > new Date()).length;
            const blacklisted = blacklist.size;
            const onlineCount = [...licenses.values()].filter(l => l.active === true).length;

            // Count by type
            const byType = { lifetime_normal: 0, lifetime_premium: 0, monthly: 0, custom: 0 };
            for (const [, data] of licenses) {
                const t = data.licenseType || 'custom';
                if (byType[t] !== undefined) byType[t]++;
                else byType.custom++;
            }

            const embed = new EmbedBuilder()
                .setTitle('📊 Dragonite Stats')
                .setColor(0x5865F2)
                .addFields(
                    { name: 'Total Licenses', value: String(totalLicenses), inline: true },
                    { name: 'Active (not expired)', value: String(activeLicenses), inline: true },
                    { name: 'Currently Online 🟢', value: String(onlineCount), inline: true },
                    { name: 'Blacklisted 🚫', value: String(blacklisted), inline: true },
                    { name: '\u200b', value: '\u200b', inline: false },
                    { name: 'Lifetime Normal', value: String(byType.lifetime_normal), inline: true },
                    { name: 'Lifetime Premium', value: String(byType.lifetime_premium), inline: true },
                    { name: 'Monthly', value: String(byType.monthly), inline: true },
                    { name: 'Custom', value: String(byType.custom), inline: true }
                );

            await interaction.reply({ embeds: [embed], ephemeral: true });
        }

        else if (commandName === 'watch') {
            const targetUser = options.getUser('user');
            const reason = options.getString('reason') || 'Monitored target lock';

            unwatchlist.delete(targetUser.id);
            watchlist.set(targetUser.id, {
                addedBy: interaction.user.tag,
                addedAt: new Date().toISOString(),
                reason,
                actionCount: 0,
                actions: []
            });
            saveData();

            await interaction.reply({
                content: `👁️ **TARGET LOCK ACTIVATED** for <@${targetUser.id}> (\`${targetUser.tag}\`).\n` +
                         `Removed from Exempt List. Watchdog security monitoring active.`,
                ephemeral: true
            });
        }

        else if (commandName === 'unwatch') {
            const targetUser = options.getUser('user');

            unwatchlist.add(targetUser.id);
            watchlist.delete(targetUser.id);
            saveData();

            await interaction.reply({
                content: `✅ Added <@${targetUser.id}> (\`${targetUser.tag}\`) to the **Exempt List**. They are now 100% exempted from Watchdog security monitoring.`,
                ephemeral: true
            });
        }

        else if (commandName === 'watchlist') {
            const embed = new EmbedBuilder()
                .setTitle('👁️ Security Watchlist & Exemptions')
                .setColor(0x5865F2)
                .setDescription(`**Watch-All Mode**: \`ENABLED 🟢\` (All server members & staff monitored by default)\n\n` +
                                `**Exempt List (Unwatched Users)**:\n${unwatchlist.size > 0 ? Array.from(unwatchlist).map(id => `• <@${id}> (\`${id}\`)`).join('\n') : '• *None (Only Server Owner is exempt)*'}`);

            if (watchlist.size > 0) {
                for (const [userId, data] of watchlist.entries()) {
                    getActiveCategoryCount(data, null);
                    embed.addFields({
                        name: `Specific Target Lock: <@${userId}> (${userId})`,
                        value: `• Reason: ${data.reason}\n• Added: <t:${Math.floor(new Date(data.addedAt).getTime() / 1000)}:R>`,
                        inline: false
                    });
                }
            }

            await interaction.reply({ embeds: [embed], ephemeral: true });
        }

        else if (commandName === 'clear') {
            await interaction.deferReply({ ephemeral: true });

            const amount = options.getInteger('amount');
            const targetUser = options.getUser('user');
            const clearAll = options.getBoolean('all') === true;

            const fetchLimit = (clearAll || !amount) ? 100 : Math.min(amount, 100);

            const fetchedMessages = await interaction.channel.messages.fetch({ limit: fetchLimit }).catch(() => null);
            if (!fetchedMessages || fetchedMessages.size === 0) {
                return interaction.editReply({ content: '❌ No messages found to clear in this channel.' });
            }

            let toDelete = Array.from(fetchedMessages.values());

            // Filter by user if specified
            if (targetUser) {
                toDelete = toDelete.filter(m => m.author.id === targetUser.id);
            }

            // Filter out messages older than 14 days per Discord API limit
            const fourteenDaysAgo = Date.now() - (14 * 24 * 60 * 60 * 1000);
            const validToDelete = toDelete.filter(m => m.createdTimestamp > fourteenDaysAgo);

            if (validToDelete.length === 0) {
                return interaction.editReply({
                    content: '❌ No valid messages found to delete (Discord API restricts bulk deleting messages older than 14 days).'
                });
            }

            const deleted = await interaction.channel.bulkDelete(validToDelete, true).catch(err => {
                console.error('[Clear Error]:', err.message);
                return null;
            });

            const deletedCount = deleted ? deleted.size : 0;
            const filterNote = targetUser ? ` from <@${targetUser.id}>` : '';
            await interaction.editReply({
                content: `✅ Successfully purged **${deletedCount}** message(s)${filterNote} in #${interaction.channel.name}.`
            });
        }

        else if (commandName === 'ticketpanel') {
            const logoUrl = 'https://dragoniteclient.fun/dragonite-logo.jpg';

            const embed = new EmbedBuilder()
                .setAuthor({ name: 'Dragonite Client', iconURL: logoUrl })
                .setTitle('Dragonite Client')
                .setURL('https://dragoniteclient.fun')
                .setColor(0x5865F2)
                .setDescription('Need help with your purchase, license, or technical issues?\nClick a button below to open a private support ticket.')
                .setThumbnail(logoUrl);

            const row = new ActionRowBuilder().addComponents(
                new ButtonBuilder().setCustomId('btn_ticket_purchase').setLabel('Purchase / License').setStyle(ButtonStyle.Primary).setEmoji('💳'),
                new ButtonBuilder().setCustomId('btn_ticket_bug').setLabel('Report Bug').setStyle(ButtonStyle.Danger).setEmoji('🐛'),
                new ButtonBuilder().setCustomId('btn_ticket_general').setLabel('General Help').setStyle(ButtonStyle.Secondary).setEmoji('❓')
            );

            await interaction.channel.send({ embeds: [embed], components: [row] });
            await interaction.reply({ content: '✅ Ticket panel created successfully!', ephemeral: true });
        }

        else if (commandName === 'invites') {
            await interaction.deferReply({ ephemeral: true });
            const targetUser = options.getUser('user') || interaction.user;
            const data = userInvites.get(targetUser.id) || { regular: 0, left: 0, fake: 0, bonus: 0 };

            const regular = data.regular || 0;
            const left = data.left || 0;
            const fake = data.fake || 0;
            const bonus = data.bonus || 0;
            const total = Math.max(0, (regular + bonus) - (left + fake));

            const avatarUrl = targetUser.displayAvatarURL({ size: 256, extension: 'png' });

            const embed = new EmbedBuilder()
                .setAuthor({ name: `${targetUser.tag}'s Invites`, iconURL: avatarUrl })
                .setTitle(`📊 Invite Statistics`)
                .setColor(0x5865F2)
                .setThumbnail(avatarUrl)
                .setDescription(`<@${targetUser.id}> currently has **${total}** net invites!`)
                .addFields(
                    { name: '✅ Regular', value: String(regular), inline: true },
                    { name: '🚪 Left', value: String(left), inline: true },
                    { name: '🤖 Fake (< 7d)', value: String(fake), inline: true },
                    { name: '🎁 Bonus', value: String(bonus), inline: true },
                    { name: '📈 Total Net', value: String(total), inline: true }
                )
                .setFooter({ text: 'Dragonite Client Invite Tracker' });

            await interaction.editReply({ embeds: [embed] });
        }

        else if (commandName === 'inviteleaderboard') {
            await interaction.deferReply({ ephemeral: true });
            const leaderboard = [];

            for (const [userId, data] of userInvites.entries()) {
                const regular = data.regular || 0;
                const left = data.left || 0;
                const fake = data.fake || 0;
                const bonus = data.bonus || 0;
                const total = Math.max(0, (regular + bonus) - (left + fake));
                if (total > 0) {
                    leaderboard.push({ userId, total, regular, left, fake, bonus });
                }
            }

            leaderboard.sort((a, b) => b.total - a.total);
            const top10 = leaderboard.slice(0, 10);

            if (top10.length === 0) {
                return interaction.editReply({ content: '📭 No invites tracked yet in this server.' });
            }

            const medalIcons = ['🥇', '🥈', '🥉'];
            const rows = top10.map((entry, index) => {
                const icon = medalIcons[index] || `**#${index + 1}**`;
                return `${icon} <@${entry.userId}> — **${entry.total}** invites (${entry.regular} reg, ${entry.left} left, ${entry.bonus} bonus)`;
            });

            const embed = new EmbedBuilder()
                .setTitle('🏆 Top Inviter Leaderboard')
                .setColor(0x5865F2)
                .setDescription(rows.join('\n\n'))
                .setFooter({ text: 'Dragonite Client Invite Tracker' });

            await interaction.editReply({ embeds: [embed] });
        }

        else if (commandName === 'addinvites') {
            await interaction.deferReply({ ephemeral: true });
            const targetUser = options.getUser('user');
            const amount = options.getInteger('amount');

            let invData = userInvites.get(targetUser.id) || { regular: 0, left: 0, fake: 0, bonus: 0 };
            invData.bonus = (invData.bonus || 0) + amount;
            userInvites.set(targetUser.id, invData);
            saveData();

            await interaction.editReply({
                content: `✅ Added **+${amount}** bonus invites to <@${targetUser.id}>. Total bonus: **${invData.bonus}**.`
            });
        }

        else if (commandName === 'removeinvites') {
            await interaction.deferReply({ ephemeral: true });
            const targetUser = options.getUser('user');
            const amount = options.getInteger('amount');

            let invData = userInvites.get(targetUser.id) || { regular: 0, left: 0, fake: 0, bonus: 0 };
            invData.bonus = Math.max(0, (invData.bonus || 0) - amount);
            userInvites.set(targetUser.id, invData);
            saveData();

            await interaction.editReply({
                content: `✅ Removed **-${amount}** bonus invites from <@${targetUser.id}>. Total bonus: **${invData.bonus}**.`
            });
        }

        else if (commandName === 'resetinvites') {
            await interaction.deferReply({ ephemeral: true });
            const targetUser = options.getUser('user');

            userInvites.delete(targetUser.id);
            saveData();

            await interaction.editReply({
                content: `✅ Reset all invite statistics for <@${targetUser.id}>.`
            });
        }

        else if (commandName === 'gstart') {
            const modal = new ModalBuilder()
                .setCustomId('modal_giveaway_create')
                .setTitle('🎉 Create New Giveaway');

            const prizeInput = new TextInputBuilder()
                .setCustomId('g_prize')
                .setLabel('Prize')
                .setPlaceholder('e.g. Dragonite Client Lifetime Key')
                .setStyle(TextInputStyle.Short)
                .setRequired(true);

            const durationInput = new TextInputBuilder()
                .setCustomId('g_duration')
                .setLabel('Duration (e.g. 10m, 1h, 2d, 1w)')
                .setPlaceholder('e.g. 1h, 2d, 1w')
                .setStyle(TextInputStyle.Short)
                .setRequired(true);

            const winnersInput = new TextInputBuilder()
                .setCustomId('g_winners')
                .setLabel('Number of Winners')
                .setPlaceholder('1')
                .setValue('1')
                .setStyle(TextInputStyle.Short)
                .setRequired(true);

            const reqInput = new TextInputBuilder()
                .setCustomId('g_reqs')
                .setLabel('Requirements (e.g. invites:2, tag:.dragonite)')
                .setPlaceholder('e.g. invites:2, tag:.dragonite or none')
                .setStyle(TextInputStyle.Short)
                .setRequired(false);

            const descInput = new TextInputBuilder()
                .setCustomId('g_desc')
                .setLabel('Description')
                .setPlaceholder('Optional giveaway description...')
                .setStyle(TextInputStyle.Paragraph)
                .setRequired(false);

            modal.addComponents(
                new ActionRowBuilder().addComponents(prizeInput),
                new ActionRowBuilder().addComponents(durationInput),
                new ActionRowBuilder().addComponents(winnersInput),
                new ActionRowBuilder().addComponents(reqInput),
                new ActionRowBuilder().addComponents(descInput)
            );

            await interaction.showModal(modal);
        }

        else if (commandName === 'gend') {
            await interaction.deferReply({ ephemeral: true });
            const searchId = options.getString('id').trim();

            let targetGiveaway = giveaways.get(searchId);
            if (!targetGiveaway) {
                for (const g of giveaways.values()) {
                    if (g.messageId === searchId || g.id === searchId) {
                        targetGiveaway = g;
                        break;
                    }
                }
            }

            if (!targetGiveaway) {
                return interaction.editReply({ content: '❌ Giveaway not found for that ID or Message ID.' });
            }

            if (targetGiveaway.ended) {
                return interaction.editReply({ content: '❌ That giveaway has already ended.' });
            }

            await endGiveaway(targetGiveaway.id, interaction.guild);
            await interaction.editReply({ content: `✅ Giveaway **${targetGiveaway.prize}** ended early!` });
        }

        else if (commandName === 'greroll') {
            await interaction.deferReply({ ephemeral: true });
            const searchId = options.getString('id').trim();

            let targetGiveaway = giveaways.get(searchId);
            if (!targetGiveaway) {
                for (const g of giveaways.values()) {
                    if (g.messageId === searchId || g.id === searchId) {
                        targetGiveaway = g;
                        break;
                    }
                }
            }

            if (!targetGiveaway) {
                return interaction.editReply({ content: '❌ Giveaway not found for that ID or Message ID.' });
            }

            const participants = targetGiveaway.participants || [];
            if (participants.length === 0) {
                return interaction.editReply({ content: '❌ No participants joined this giveaway.' });
            }

            const newWinner = participants[Math.floor(Math.random() * participants.length)];
            const channel = interaction.guild.channels.cache.get(targetGiveaway.channelId);

            if (channel) {
                channel.send({
                    content: `🎉 **GIVEAWAY REROLL** 🎉\nNew Winner for **${targetGiveaway.prize}**: <@${newWinner}>! 🏆`
                }).catch(() => {});
            }

            await interaction.editReply({ content: `✅ Rerolled new winner: <@${newWinner}>!` });
        }

        else if (commandName === 'download') {
            await interaction.deferReply({ ephemeral: true });

            const allowedRoleIds = ['1449144445160394983', '1189292325084606506'];
            const hasAccess = isOwner(interaction) || (interaction.member && interaction.member.roles.cache.some(r => allowedRoleIds.includes(r.id)));

            if (!hasAccess) {
                return interaction.editReply({
                    content: 'You need the Customer or Media role to use /download.'
                });
            }

            const version = options.getString('version');
            const displayVersion = version === '1.21-1.21.1' ? '1.21 / 1.21.1' : version;

            const possibleFileNames = [
                'DragoniteClient.jar',
                'dragoniteclient.jar',
                'Dragonite.jar',
                'dragonite.jar',
                `dragonite-${version}.jar`,
                'cloth-config-15.0.140.jar'
            ];

            const searchDirs = [
                '/root/private',
                '/root/private/clients',
                '/root/website/website/public/downloads',
                '/root/website/website/public',
                '/root/dragonite-auth/downloads',
                '/root/dragonite-auth/public/downloads',
                '/root/dragonite-auth/public',
                '/root/dragonite-auth',
                path.join(__dirname, 'downloads'),
                path.join(__dirname, 'public', 'downloads'),
                path.join(__dirname, 'public'),
                __dirname
            ];

            let foundPath = null;

            for (const name of possibleFileNames) {
                for (const dir of searchDirs) {
                    const testPath = path.join(dir, name);
                    if (fs.existsSync(testPath)) {
                        foundPath = testPath;
                        break;
                    }
                }
                if (foundPath) break;
            }

            if (!foundPath) {
                return interaction.editReply({
                    content: 'Client file was not found on the VPS. Make sure `DragoniteClient.jar` exists in `/root/private/`.'
                });
            }

            // Always disguise the attached file name as cloth-config-15.0.140.jar (stealth mod name)
            const attachment = new AttachmentBuilder(foundPath, { name: 'cloth-config-15.0.140.jar' });
            return interaction.editReply({
                content: `Dragonite Client (${displayVersion}):`,
                files: [attachment]
            });
        }
    } catch (error) {
        console.error('[Bot] Error:', error);
        const msg = `❌ Error: ${error.message}`;
        if (interaction.deferred || interaction.replied) {
            await interaction.editReply({ content: msg }).catch(() => {});
        } else {
            await interaction.reply({ content: msg, ephemeral: true }).catch(() => {});
        }
    }
});

/** Local HTTP bridge so auth-server can ping via this bot (same token, no extra API setup). */
function startAlertBridge() {
    const channelId = (process.env.DISCORD_ALERT_CHANNEL_ID || '').trim().replace(/^["']|["']$/g, '');
    const secret = (process.env.INTERNAL_ALERT_SECRET || process.env.ADMIN_KEY || '').trim();
    const port = Number(process.env.ALERT_BRIDGE_PORT || 8001);
    const roleId = (process.env.DISCORD_PING_ROLE_ID || '').trim().replace(/^["']|["']$/g, '');
    
    const recentAlertsCache = new Map();

    if (!channelId) {
        console.log('[Bot] Alert bridge: off (set DISCORD_ALERT_CHANNEL_ID in .env)');
        return;
    }
    if (!secret) {
        console.log('[Bot] Alert bridge: off (set INTERNAL_ALERT_SECRET or ADMIN_KEY)');
        return;
    }

    const server = http.createServer((req, res) => {
        if (req.method !== 'POST' || req.url !== '/internal/ping') {
            res.writeHead(404);
            res.end();
            return;
        }
        let raw = '';
        req.on('data', (chunk) => { raw += chunk; });
        req.on('end', async () => {
            try {
                const hdr = req.headers['x-alert-secret'];
                const body = raw ? JSON.parse(raw) : {};
                const provided = (typeof hdr === 'string' ? hdr : '') || body.secret || '';
                if (provided !== secret) {
                    res.writeHead(401, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ ok: false, error: 'Unauthorized' }));
                    return;
                }

                let targetChannelId = channelId;
                const spamChannelId = (process.env.DISCORD_SPAM_CHANNEL_ID || '').trim().replace(/^["']|["']$/g, '');
                
                let content = String(body.content || '').slice(0, 1900);
                const lowerContent = content.toLowerCase();
                const isSpamMsg = lowerContent.includes('login') || 
                                  lowerContent.includes('auto-login') || 
                                  lowerContent.includes('bye') || 
                                  lowerContent.includes('logout');

                if (isSpamMsg && spamChannelId) {
                    targetChannelId = spamChannelId;
                }

                // De-duplicate consecutive user logs within 20s
                const licenseMatch = content.match(/License:\s*([^\n\r]+)/i);
                if (licenseMatch && isSpamMsg) {
                    const licenseKey = licenseMatch[1].trim();
                    const cacheKey = `${licenseKey}:${lowerContent.includes('bye') || lowerContent.includes('logout') ? 'logout' : 'login'}`;
                    const now = Date.now();
                    const lastAlertTime = recentAlertsCache.get(cacheKey) || 0;
                    if (now - lastAlertTime < 20000) {
                        res.writeHead(200, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ ok: true, note: 'duplicate_suppressed' }));
                        return;
                    }
                    recentAlertsCache.set(cacheKey, now);

                    if (recentAlertsCache.size > 1000) {
                        for (const [k, v] of recentAlertsCache.entries()) {
                            if (now - v > 60000) recentAlertsCache.delete(k);
                        }
                    }
                }

                const channel = await client.channels.fetch(targetChannelId);
                if (!channel || !channel.isTextBased()) {
                    res.writeHead(503, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ ok: false, error: 'Channel not found or not text' }));
                    return;
                }

                let allowedMentions = { parse: [] };
                const useRole = (body.roleId || roleId || '').trim();
                if (useRole) {
                    content = `<@&${useRole}>\n${content}`;
                    allowedMentions = { roles: [useRole] };
                } else if (body.pingEveryone === true) {
                    content = `@everyone\n${content}`;
                    allowedMentions = { parse: ['everyone'] };
                }

                console.log(`[Bot] Sending alert to Guild: "${channel.guild ? channel.guild.name : 'Unknown'}" | Channel: "#${channel.name}" (ID: ${channel.id})`);
                await channel.send({ content, allowedMentions });
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ ok: true }));
            } catch (e) {
                console.error('[Bot] Alert bridge error:', e.message);
                res.writeHead(500, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ ok: false, error: e.message }));
            }
        });
    });

    server.listen(port, '127.0.0.1', () => {
        console.log(`[Bot] Alert bridge: http://127.0.0.1:${port}/internal/ping → channel ${channelId}`);
    });
}

// Ready event
client.once('ready', () => {
    console.log('========================================');
    console.log('    Dragonite License Bot Online!       ');
    console.log('========================================');
    console.log(`Bot: ${client.user.tag}`);
    console.log(`Servers: ${client.guilds.cache.size}`);

    // Cache all guild invites on startup for invite tracking
    client.guilds.cache.forEach(guild => cacheGuildInvites(guild));

    // Resume timers for active giveaways on bot startup
    giveaways.forEach((giveaway, gId) => {
        if (!giveaway.ended) {
            const timeLeft = giveaway.endAt - Date.now();
            const targetGuild = client.guilds.cache.get(process.env.GUILD_ID) || client.guilds.cache.first();
            if (timeLeft <= 0) {
                if (targetGuild) endGiveaway(gId, targetGuild);
            } else {
                setTimeout(() => {
                    const g = client.guilds.cache.get(process.env.GUILD_ID) || client.guilds.cache.first();
                    if (g) endGiveaway(gId, g);
                }, timeLeft);
            }
        }
    });
    console.log();
    console.log('Commands:');
    console.log('  /license create <type> [duration]');
    console.log('  /license list');
    console.log('  /license info|history|reset|hwidreset|clear');
    console.log('  /license hwidreset <key> · /license reset <type> <value> = HWID only');
    console.log('  /license clear = new ID, wipe player info');
    console.log('  /license delete <license>');
    console.log('  /license online');
    console.log('  /blacklist add <type> <value> [reason]');
    console.log('  /blacklist remove <type> <value>  (clears ALL ban keys for that person)');
    console.log('  /blacklist list');
    console.log('  /stats');
    console.log();
    console.log('License Types:');
    console.log('  lifetime_normal = Lifetime Normal');
    console.log('  lifetime_premium = Lifetime Premium');
    console.log('  monthly = Monthly (30 days)');
    console.log('  custom = Custom (set duration)');
    console.log();
    console.log('========================================');
    
    // Register commands: Instant per-guild registration for all allowed guild IDs + Global fallback
    const payload = commands.map(cmd => typeof cmd.toJSON === 'function' ? cmd.toJSON() : cmd);
    const allowedGuildIds = getAllowedGuildIds();

    // Automatic Server Lockdown: Leave any unauthorized server instantly
    if (allowedGuildIds.length > 0) {
        client.guilds.cache.forEach(guild => {
            if (!allowedGuildIds.includes(guild.id)) {
                console.warn(`[SECURITY LOCKDOWN] Leaving unauthorized server: "${guild.name}" (${guild.id})`);
                guild.leave().catch(() => {});
            }
        });

        for (const gId of allowedGuildIds) {
            client.guilds.fetch(gId).then(guild => {
                if (guild) {
                    guild.commands.set(payload).then(() => {
                        console.log(`[Bot] Registered slash commands INSTANTLY for Guild: ${guild.name} (${guild.id})`);
                    }).catch(e => console.error('[Bot] Instant guild command register error:', e.message));
                }
            }).catch(() => {});
        }
        // Clear global commands to eliminate duplicate commands in Discord UI
        client.application.commands.set([]).catch(() => {});
    } else {
        client.application.commands.set(payload).then(() => {
            console.log(`[Bot] Registered ${payload.length} global slash commands successfully.`);
        }).catch(e => console.error('[Bot] Global command register error:', e.message));
    }

    const channelId = (process.env.DISCORD_ALERT_CHANNEL_ID || '').trim().replace(/^["']|["']$/g, '');
    if (channelId) {
        client.channels.fetch(channelId).then((channel) => {
            if (channel && channel.isTextBased()) {
                return channel.send({
                    content: `[DragoniteAuth] 🤖 License bot online — ${client.user.tag}\nServers: ${client.guilds.cache.size}`,
                    allowedMentions: { parse: [] }
                });
            }
        }).catch((e) => console.error('[Bot] Startup channel message failed:', e.message));
    } else {
        postStartupWebhook(`[DragoniteAuth] 🤖 License bot online — ${client.user.tag}\nServers: ${client.guilds.cache.size}`);
    }
    startAlertBridge();
});

// Automated Customer Guild Role Auto-Assignment on Join
client.on('guildMemberAdd', async (member) => {
    const customerGuildId = (process.env.DISCORD_CUSTOMER_GUILD_ID || '').trim().replace(/^["']|["']$/g, '');
    if (!customerGuildId || member.guild.id !== customerGuildId) return;

    console.log(`[Bot] Member joined Customer Guild: ${member.user.tag} (${member.id})`);
    
    // Refresh local license cache
    reloadLicensesFromDisk();
    
    let isCustomer = false;
    let isPremium = false;
    
    for (const [key, record] of licenses.entries()) {
        if (record && record.discordId && String(record.discordId) === String(member.id)) {
            isCustomer = true;
            if (record.licenseType === 'lifetime_premium') {
                isPremium = true;
            }
        }
    }
    
    if (isCustomer) {
        console.log(`[Bot] Auto-match: ${member.user.tag} is a verified customer! Assiging roles...`);
        const customerRoleId = (process.env.DISCORD_CUSTOMER_ROLE_ID || '').trim().replace(/^["']|["']$/g, '');
        const premiumRoleId = (process.env.DISCORD_PREMIUM_ROLE_ID || '').trim().replace(/^["']|["']$/g, '');
        
        try {
            const rolesToAdd = [];
            if (customerRoleId) rolesToAdd.push(customerRoleId);
            if (isPremium && premiumRoleId) rolesToAdd.push(premiumRoleId);
            
            if (rolesToAdd.length > 0) {
                await member.roles.add(rolesToAdd);
                console.log(`[Bot] Auto-assigned roles to ${member.user.tag}`);
            }
        } catch (err) {
            console.error(`[Bot] Auto-assignment failed for ${member.user.tag}:`, err.message);
        }
    } else {
        console.log(`[Bot] Member ${member.user.tag} has no linked purchase. No roles assigned.`);
    }
});

// Start
loadData();
if (!CONFIG.DISCORD_TOKEN) {
    console.error('[Bot] DISCORD_TOKEN missing in .env — bot will not start.');
    process.exit(1);
}
client.login(CONFIG.DISCORD_TOKEN);

// ============================================
// PER-CATEGORY WATCHLIST STRIKE & NEUTRALIZE ENGINE
// ============================================
async function processWatchlistStrike(guild, executorId, category, actionType, targetName, extraAction = null) {
    if (!isWatchedUser(executorId, guild)) return;

    let watchData = watchlist.get(executorId);
    if (!watchData) {
        watchData = {
            addedBy: 'SYSTEM_WATCH_ALL',
            addedAt: new Date().toISOString(),
            reason: 'Auto-monitored by Watch-All Mode',
            actionCount: 0,
            actions: []
        };
        watchlist.set(executorId, watchData);
    }
    if (!Array.isArray(watchData.actions)) watchData.actions = [];

    // Deduplicate duplicate audit events within 3 seconds for the same category & action
    const now = Date.now();
    const lastAction = watchData.actions[watchData.actions.length - 1];
    if (lastAction && lastAction.category === category && lastAction.type === actionType && (now - new Date(lastAction.time).getTime() < 3000)) {
        return;
    }

    watchData.actions.push({ category, type: actionType, target: targetName, time: new Date().toISOString() });
    const activeCatCount = getActiveCategoryCount(watchData, category);
    saveData();

    console.warn(`[WATCHLIST STRIKE] User (${executorId}) triggered [${category}] strike #${activeCatCount}/2 (${actionType} on ${targetName})`);

    const ownerId = process.env.DISCORD_OWNER_ID || guild?.ownerId;
    const owner = ownerId ? await client.users.fetch(ownerId).catch(() => null) : null;

    const categoryTitles = {
        ADMIN_ESCALATION: '🚨 Unauthorized Admin Permission Escalation',
        MODERATION: '🛡️ Moderation (Bans/Kicks/Timeouts)',
        PING: '📢 Mass Pings (@\u200beveryone/@\u200bhere)',
        CHANNEL_CREATE: '➕ Channel Creation',
        CHANNEL_DELETE: '🗑️ Channel Deletion',
        CHANNEL_UPDATE: '⚙️ Channel Move / Edit',
        ROLE_CREATE: '👑 Role Creation',
        ROLE_DELETE: '❌ Role Deletion',
        ROLE_UPDATE: '✏️ Role Move / Edit',
        GUILD_UPDATE: '🌐 Server/Guild Settings Edit',
        WEBHOOK: '🔗 Webhook Creation / Edit',
        BOT_ADD: '🤖 Unauthorized Bot Invite'
    };
    const catLabel = categoryTitles[category] || category;

    const maxAllowed = (category === 'WEBHOOK' || category === 'BOT_ADD' || category === 'ADMIN_ESCALATION' || category === 'GUILD_UPDATE' || category === 'PING') ? 0 : 2;

    if (activeCatCount > maxAllowed) {
        // AGGRO MODE: Delete webhook/bot/role if provided, strip all roles, kick & ban developer
        if (extraAction && typeof extraAction === 'function') {
            await extraAction().catch(() => {});
        }

        try {
            const devMember = guild ? await guild.members.fetch(executorId).catch(() => null) : null;
            if (devMember) {
                await devMember.roles.set([], `Watchlist threshold exceeded in ${catLabel}`).catch(() => {});
                await devMember.kick(`Watchlist anti-nuke: Zero-tolerance / threshold exceeded in ${catLabel} (${actionType})`).catch(() => {});
            }
            console.error(`[WATCHLIST NEUTRALIZED] Monitored user (${executorId}) stripped of roles and kicked for ${catLabel}!`);
        } catch (err) {
            console.error('[Watchlist Neutralize Error]:', err.message);
        }

        // Reset strike history after neutralizing so rejoins/stale audit events don't re-kick repeatedly
        watchData.actions = [];
        watchData.actionCount = 0;
        saveData();

        if (owner) {
            owner.send({
                content: `🚨 **CRITICAL WATCHLIST ALERT**: Monitored user <@${executorId}> triggered violation in **${catLabel}** (**${actionType}** on \`${targetName}\`)!\n` +
                       `✅ **NEUTRALIZED**: Unauthorized action reverted/neutralized. Stripped ALL roles and kicked user from server. Strike history reset.`,
                allowedMentions: { parse: ['users'] }
            }).catch(() => {});
        }
    } else {
        if (owner) {
            owner.send({
                content: `⚠️ **WATCHLIST WARNING**: Monitored user <@${executorId}> triggered violation in **${catLabel}** (Action #${activeCatCount}/2: **${actionType}** on \`${targetName}\`). Allowed through. (Expires in 24h)`,
                allowedMentions: { parse: ['users'] }
            }).catch(() => {});
        }
    }
}

// Auto-Moderation Rate Limit, Duplicate & Progressive Escalation Tracker
const userSpamHistory = new Map();
const userViolationMap = new Map(); // userId -> { timestamps: [number] }
const everyoneAttemptMap = new Map(); // userId -> { timestamps: [] }
const everyoneRealPingMap = new Map(); // userId -> { timestamps: [] }

async function handleEveryoneMention(message) {
    const userId = message.author.id;
    const now = Date.now();

    // Check if the ping actually fired (Discord converted it into a real mention)
    const actuallyPinged = message.mentions.everyone === true;

    if (!actuallyPinged) {
        // Track 1: Text Attempt (User typed @everyone/@here in text without perms -> No server ping occurred)
        await message.delete().catch(() => {});

        let data = everyoneAttemptMap.get(userId) || { timestamps: [] };
        data.timestamps = (data.timestamps || []).filter(t => now - t < 7 * 24 * 60 * 60 * 1000);
        data.timestamps.push(now);
        everyoneAttemptMap.set(userId, data);

        const strike = data.timestamps.length;
        if (strike === 1) {
            message.channel.send({
                content: `⚠️ <@${userId}> Do not type @\u200beveryone or @\u200bhere — warning issued (Strike #1/3).`,
                allowedMentions: { parse: ['users'] }
            }).then(m => setTimeout(() => m.delete().catch(() => {}), 5000)).catch(() => {});
        } else if (strike === 2) {
            if (message.member && message.member.moderatable) {
                await message.member.timeout(10 * 1000, `@everyone attempt strike #${strike}`).catch(() => {});
            }
            message.channel.send({
                content: `⚠️ <@${userId}> Timed out for 10 seconds due to repeated @\u200beveryone attempts (Strike #2/3).`,
                allowedMentions: { parse: ['users'] }
            }).then(m => setTimeout(() => m.delete().catch(() => {}), 5000)).catch(() => {});
        } else {
            // Strike 3+ -> 3-Day Timeout (No kick!)
            if (message.member && message.member.moderatable) {
                await message.member.timeout(3 * 24 * 60 * 60 * 1000, `@everyone attempt strike #${strike}`).catch(() => {});
            }
            message.channel.send({
                content: `🚨 <@${userId}> Timed out for 3 days due to repeated @\u200beveryone attempts (Strike #3/3).`,
                allowedMentions: { parse: ['users'] }
            }).then(m => setTimeout(() => m.delete().catch(() => {}), 8000)).catch(() => {});
        }
        return true;
    }

    // Track 2: Real Ping Fired (User DOES have MentionEveryone perms -> Discord Moderator / Admin)
    let data = everyoneRealPingMap.get(userId) || { timestamps: [] };
    data.timestamps = (data.timestamps || []).filter(t => now - t < 24 * 60 * 60 * 1000);
    data.timestamps.push(now);
    everyoneRealPingMap.set(userId, data);

    const strike = data.timestamps.length;

    // Strikes 1, 2, and 3: ALLOWED for Discord Moderators, but send detailed warning DM!
    if (strike <= 3) {
        const expList = data.timestamps.map((t, idx) => {
            const expSec = Math.floor((t + (24 * 60 * 60 * 1000)) / 1000);
            const issuedSec = Math.floor(t / 1000);
            return `• Warning #${idx + 1}: Issued <t:${issuedSec}:R> — Expires <t:${expSec}:R>`;
        }).join('\n');

        const dmEmbed = new EmbedBuilder()
            .setTitle(`⚠️ @everyone Mass Ping Notice (${strike}/3 Warnings)`)
            .setColor(strike === 3 ? 0xFF9900 : 0x5865F2)
            .setDescription(`Yo <@${userId}>! Just so you know, we have a limit on @everyone mass pings.\n\n` +
                `📌 **Rule**: Maximum is **3 pings per 24 hours**. On your **4th ping** within 24h you will be kicked and your license deactivated.\n\n` +
                `📊 **Your Active Warnings (${strike}/3)**:\n${expList}`)
            .setFooter({ text: 'Dragonite Client Security System' });

        message.author.send({ embeds: [dmEmbed] }).catch(err => {
            console.warn(`[DM Warning Error] Could not send ping warning DM to ${message.author.tag}:`, err.message);
        });

        return false; // Let the moderator's ping go through
    }

    // Strike 4+ (4th real ping within 24h -> Nuke attempt / Compromised moderator!)
    everyoneRealPingMap.delete(userId);
    await message.delete().catch(() => {});

    // 1. Strip all roles & Kick from server
    if (message.member && message.member.kickable) {
        await message.member.roles.set([], 'Real @everyone ping strike 4 — deactivated & kicked').catch(() => {});
        await message.member.kick('Real @everyone ping strike 4/4').catch(() => {});
    }

    // 2. Blacklist & Deactivate License Hook
    for (const [key, record] of licenses.entries()) {
        if (record && String(record.discordId) === String(userId)) {
            record.active = false;
            record.blacklisted = true;
            record.banReason = 'Real @everyone mass ping abuse (Strike 4/4)';
        }
    }
    blacklist.set(userId, {
        reason: 'Real @everyone mass ping abuse (Strike 4/4)',
        typeLabel: 'Discord User ID',
        rawValue: userId,
        addedAt: new Date().toISOString()
    });
    saveData();

    message.channel.send({
        content: `🚨🚨🚨 <@${userId}> pinged @\u200beveryone **4 times in 24h** — **stripped, kicked, and license deactivated**!`,
        allowedMentions: { parse: ['users'] }
    }).then(m => setTimeout(() => m.delete().catch(() => {}), 10000)).catch(() => {});

    return true;
}

async function applyAutoModTimeout(message, reason) {
    const userId = message.author.id;
    const now = Date.now();
    let vData = userViolationMap.get(userId) || { timestamps: [] };

    // Rolling strike expiration: Keep strikes from the past 7 days (7 * 24 * 60 * 60 * 1000)
    vData.timestamps = (vData.timestamps || []).filter(t => (now - t) < (7 * 24 * 60 * 60 * 1000));
    vData.timestamps.push(now);
    userViolationMap.set(userId, vData);

    const strikeCount = vData.timestamps.length;
    const cleanReason = (reason || '').replace(/@everyone/g, '@\u200beveryone').replace(/@here/g, '@\u200bhere');

    if (strikeCount >= 3) {
        // STRIKE 3: STRIP ALL ROLES & KICK FROM SERVER
        userViolationMap.delete(userId);
        if (message.member && message.member.kickable) {
            await message.member.roles.set([], `Auto-Mod Strike 3: ${reason}`).catch(() => {});
            await message.member.kick(`Auto-Mod Strike 3: ${reason}`).catch(() => {});
        }
        message.channel.send({
            content: `🚨 <@${userId}> reached **Strike #3/3** (${cleanReason}). Stripped all roles and **kicked from server**!`,
            allowedMentions: { parse: ['users'] }
        })
            .then(warnMsg => setTimeout(() => warnMsg.delete().catch(() => {}), 8000))
            .catch(() => {});
        return;
    }

    let durationMs = 10 * 1000; // Strike 1: 10 seconds
    let durationLabel = '10 seconds';

    if (strikeCount === 2) {
        durationMs = 3 * 24 * 60 * 60 * 1000; // Strike 2: 3 Days (72 Hours)
        durationLabel = '3 days';
    }

    if (message.member && message.member.moderatable) {
        await message.member.timeout(durationMs, `Auto-Mod: ${reason} (Strike #${strikeCount}/3)`).catch(() => {});
    }

    message.channel.send({
        content: `⚠️ <@${userId}> You have been timed out for **${durationLabel}** (${cleanReason} — Strike #${strikeCount}/3). (Strikes expire after 7 days)`,
        allowedMentions: { parse: ['users'] }
    })
        .then(warnMsg => setTimeout(() => warnMsg.delete().catch(() => {}), 5000))
        .catch(() => {});
}

async function runAutoModeration(message) {
    if (!message.guild || message.author.bot) return false;

    // Allow Auto-Mod across all authorized guilds
    const allowedGuilds = getAllowedGuildIds();
    if (allowedGuilds.length > 0 && !allowedGuilds.includes(message.guild.id)) return false;

    // Exempt Bot Owner / Server Owner
    const ownerId = (process.env.DISCORD_OWNER_ID || message.guild.ownerId || '').trim().replace(/^["']|["']$/g, '');
    if (ownerId && message.author.id === ownerId) return false;

    // Exempt Unwatchlist (users explicitly exempted via /unwatch)
    if (unwatchlist.has(message.author.id)) return false;

    // Optional env exclusions if specified in DISCORD_AUTOMOD_EXCLUDED_CHANNELS
    const envExcluded = (process.env.DISCORD_AUTOMOD_EXCLUDED_CHANNELS || '').split(',').map(id => id.trim().replace(/^["']|["']$/g, '')).filter(Boolean);
    if (envExcluded.includes(message.channel.id)) return false;

    const now = Date.now();
    const userId = message.author.id;
    let history = userSpamHistory.get(userId) || { timestamps: [], lastContent: '', lastContentCount: 0, lastContentTime: 0, dupMessages: [] };

    // 1. Discord Invite Filter
    const inviteRegex = /(discord\.gg|discord\.com\/invite|discordapp\.com\/invite)\/[a-zA-Z0-9]+/i;
    if (inviteRegex.test(message.content)) {
        await message.delete().catch(() => {});
        message.channel.send(`⚠️ <@${userId}> Discord invite links are not allowed.`)
            .then(msg => setTimeout(() => msg.delete().catch(() => {}), 5000))
            .catch(() => {});
        return true;
    }

    // 2. Mass Mention Filter (>5 mentions)
    const mentionCount = (message.mentions.users?.size || 0) + (message.mentions.roles?.size || 0);
    if (mentionCount > 5) {
        await message.delete().catch(() => {});
        message.channel.send(`⚠️ <@${userId}> Mass mentions are not allowed.`)
            .then(msg => setTimeout(() => msg.delete().catch(() => {}), 5000))
            .catch(() => {});
        return true;
    }

    // 3. Repeated Duplicate Message Filter (3 duplicates in 10s — Purge extra duplicates + Progressive Timeout)
    const cleanContent = message.content.trim().toLowerCase();
    if (cleanContent.length > 3) {
        if (!Array.isArray(history.dupMessages)) history.dupMessages = [];

        if (history.lastContent === cleanContent && (now - history.lastContentTime < 10000)) {
            history.lastContentCount++;
            history.dupMessages.push(message);
        } else {
            history.lastContent = cleanContent;
            history.lastContentCount = 1;
            history.lastContentTime = now;
            history.dupMessages = [message];
        }

        if (history.lastContentCount >= 3) {
            const toDeleteDups = history.dupMessages.slice(1);
            history.lastContentCount = 0;
            history.dupMessages = [];
            for (const m of toDeleteDups) {
                m.delete().catch(() => {});
            }
            await applyAutoModTimeout(message, 'Duplicate message spam');
            return true;
        }
    }

    // 4. Fast Spam Rate Limit Filter (5 messages within 4 seconds)
    history.timestamps.push(now);
    history.timestamps = history.timestamps.filter(t => now - t < 4000);

    if (history.timestamps.length >= 5) {
        history.timestamps = [];
        await applyAutoModTimeout(message, 'Message spam rate limit exceeded');
        return true;
    }

    userSpamHistory.set(userId, history);
    return false;
}

// ─────────────────────────────────────────────
// DISCORD BOT EVENT LISTENERS
// ─────────────────────────────────────────────

client.on('messageCreate', async (message) => {
    try {
        if (message.author.bot) return;

        // 1. @everyone or @here Mention Handler (Allows 2 real pings for Admins/Mods, kicks on 3rd real ping in 24h)
        if (message.content.includes('@everyone') || message.content.includes('@here') || message.mentions.everyone) {
            const handled = await handleEveryoneMention(message);
            if (handled) return;
        }

        // 2. Run Main Server Auto-Moderation
        const autoModHandled = await runAutoModeration(message);
        if (autoModHandled) return;

        // MRBEAST SCAM FILTER
        let imgCount = message.attachments.filter(att => {
            const contentType = att.contentType || '';
            return contentType.startsWith('image/') || /\.(png|jpg|jpeg|webp|gif)$/i.test(att.name || '');
        }).size;

        if (message.messageSnapshots && message.messageSnapshots.size > 0) {
            message.messageSnapshots.forEach(snapshot => {
                if (snapshot.attachments) {
                    imgCount += snapshot.attachments.filter(att => {
                        const contentType = att.contentType || '';
                        return contentType.startsWith('image/') || /\.(png|jpg|jpeg|webp|gif)$/i.test(att.name || '');
                    }).size;
                }
            });
        }

        if (imgCount === 4) {
            await message.delete().catch(() => {});
            await message.author.send("Mrbeast Scam Detected.").catch(() => {});
        }
    } catch (err) {
        console.error('Error in messageCreate listener:', err.message);
    }
});

// 2. Audit Log Stream Listener
client.on('guildAuditLogEntryCreate', async (entry, guild) => {
    try {
        const executorId = entry.executorId || entry.executor?.id;
        const targetGuild = guild || entry.guild || (client.guilds.cache.first());
        if (!executorId || !isWatchedUser(executorId, targetGuild)) return;

        let category = null;
        let actionName = AuditLogEvent[entry.action] || `AuditAction_${entry.action}`;

        if (entry.action === AuditLogEvent.MemberBanAdd || entry.action === AuditLogEvent.MemberKick) {
            category = 'MODERATION';
        } else if (entry.action === AuditLogEvent.MemberUpdate) {
            const isTimeout = Array.isArray(entry.changes) && entry.changes.some(c => c.key === 'communication_disabled_until');
            if (!isTimeout) return; // Ignore nickname / role updates
            category = 'MODERATION';
            actionName = 'MemberTimeout';
        } else if (entry.action === AuditLogEvent.RoleCreate || entry.action === AuditLogEvent.RoleUpdate) {
            // Check for Administrator permission escalation
            const role = entry.target || (targetGuild ? targetGuild.roles.cache.get(entry.targetId) : null);
            let hasAdmin = role && role.permissions && role.permissions.has(PermissionFlagsBits.Administrator);
            if (!hasAdmin && Array.isArray(entry.changes)) {
                const permChange = entry.changes.find(c => c.key === 'permissions');
                if (permChange) {
                    try {
                        const newBits = BigInt(permChange.new || '0');
                        if ((newBits & PermissionFlagsBits.Administrator) === PermissionFlagsBits.Administrator) {
                            hasAdmin = true;
                        }
                    } catch (e) {}
                }
            }

            if (hasAdmin) {
                category = 'ADMIN_ESCALATION';
                actionName = entry.action === AuditLogEvent.RoleCreate ? 'RoleCreate (Administrator)' : 'RoleUpdate (Administrator)';
            } else {
                category = entry.action === AuditLogEvent.RoleCreate ? 'ROLE_CREATE' : 'ROLE_UPDATE';
            }
        } else if (entry.action === AuditLogEvent.MemberRoleUpdate) {
            // Check if watched developer granted an Administrator role to any user
            let grantedAdminRole = null;
            if (Array.isArray(entry.changes)) {
                const roleAddChange = entry.changes.find(c => c.key === '$add');
                if (roleAddChange && Array.isArray(roleAddChange.new)) {
                    for (const rObj of roleAddChange.new) {
                        const r = targetGuild ? targetGuild.roles.cache.get(rObj.id) : null;
                        if (r && r.permissions.has(PermissionFlagsBits.Administrator)) {
                            grantedAdminRole = r;
                            break;
                        }
                    }
                }
            }

            if (grantedAdminRole) {
                category = 'ADMIN_ESCALATION';
                actionName = `MemberRoleUpdate (Granted Admin Role: ${grantedAdminRole.name})`;
            } else {
                return; // Normal role changes ignored
            }
        } else if (entry.action === AuditLogEvent.ChannelCreate) {
            const chanName = (entry.target?.name || entry.targetId || '').toLowerCase();
            if (chanName.startsWith('ticket-')) return; // Ignore support ticket channel creation
            category = 'CHANNEL_CREATE';
        } else if (entry.action === AuditLogEvent.ChannelDelete) {
            const chanName = (entry.target?.name || entry.targetId || '').toLowerCase();
            if (chanName.startsWith('ticket-')) return; // Ignore support ticket channel deletion
            category = 'CHANNEL_DELETE';
        } else if (entry.action === AuditLogEvent.ChannelUpdate || entry.action === AuditLogEvent.ChannelOverwriteCreate || entry.action === AuditLogEvent.ChannelOverwriteUpdate || entry.action === AuditLogEvent.ChannelOverwriteDelete) {
            const chanName = (entry.target?.name || entry.targetId || '').toLowerCase();
            if (chanName.startsWith('ticket-')) return; // Ignore support ticket channel edits
            category = 'CHANNEL_UPDATE';
        } else if (entry.action === AuditLogEvent.RoleDelete) {
            category = 'ROLE_DELETE';
        } else if (entry.action === AuditLogEvent.GuildUpdate) {
            category = 'GUILD_UPDATE';
        } else if (entry.action === AuditLogEvent.WebhookCreate || entry.action === AuditLogEvent.WebhookUpdate || entry.action === AuditLogEvent.WebhookDelete) {
            category = 'WEBHOOK';
        } else if (entry.action === AuditLogEvent.BotAdd) {
            category = 'BOT_ADD';
        }

        if (!category) return; // Ignore unrecognized/safe events

        const targetName = entry.target ? (entry.target.tag || entry.target.name || entry.target.id || 'Target') : 'Server Target';

        // Reversion / Auto-Cleanup callback for Webhooks, Bot Invites & Admin Escalations
        const extraRevertAction = async () => {
            if (!targetGuild) return;
            if (category === 'WEBHOOK') {
                const targetId = entry.targetId || entry.target?.id;
                if (targetId) {
                    const webhooks = await targetGuild.fetchWebhooks().catch(() => null);
                    if (webhooks && webhooks.has(targetId)) {
                        await webhooks.get(targetId).delete('Watchlist anti-nuke: Unauthorized webhook creation').catch(() => {});
                        console.warn(`[REVERT SUCCESS] Auto-deleted created webhook: ${targetName} (${targetId})`);
                    }
                }
            } else if (category === 'BOT_ADD') {
                const botId = entry.targetId || entry.target?.id;
                if (botId) {
                    await targetGuild.members.kick(botId, 'Watchlist anti-nuke: Unauthorized bot invite').catch(() => {});
                    await targetGuild.bans.create(botId, { reason: 'Watchlist anti-nuke: Unauthorized bot invite' }).catch(() => {});
                    console.warn(`[REVERT SUCCESS] Auto-kicked & banned invited bot: ${targetName} (${botId})`);
                }
            } else if (category === 'ADMIN_ESCALATION') {
                if (entry.action === AuditLogEvent.RoleCreate) {
                    const roleId = entry.targetId || entry.target?.id;
                    if (roleId) {
                        const r = targetGuild.roles.cache.get(roleId);
                        if (r) {
                            await r.delete('Watchlist anti-nuke: Reverted unauthorized Admin role creation').catch(() => {});
                            console.warn(`[REVERT SUCCESS] Auto-deleted created Admin role: ${r.name}`);
                        }
                    }
                } else if (entry.action === AuditLogEvent.RoleUpdate) {
                    const roleId = entry.targetId || entry.target?.id;
                    if (roleId) {
                        const r = targetGuild.roles.cache.get(roleId);
                        if (r && r.permissions.has(PermissionFlagsBits.Administrator)) {
                            await r.setPermissions(r.permissions.remove(PermissionFlagsBits.Administrator), 'Watchlist anti-nuke: Reverted Admin permission escalation').catch(() => {});
                            console.warn(`[REVERT SUCCESS] Removed Administrator permission from role: ${r.name}`);
                        }
                    }
                } else if (entry.action === AuditLogEvent.MemberRoleUpdate) {
                    const targetMemberId = entry.targetId || entry.target?.id;
                    if (targetMemberId) {
                        const m = await targetGuild.members.fetch(targetMemberId).catch(() => null);
                        if (m) {
                            const adminRoles = m.roles.cache.filter(r => r.permissions.has(PermissionFlagsBits.Administrator));
                            if (adminRoles.size > 0) {
                                await m.roles.remove(adminRoles, 'Watchlist anti-nuke: Reverted unauthorized Admin role grant').catch(() => {});
                                console.warn(`[REVERT SUCCESS] Stripped Admin roles from member: ${m.user.tag}`);
                            }
                        }
                    }
                }
            } else if (category === 'GUILD_UPDATE') {
                if (Array.isArray(entry.changes) && targetGuild) {
                    const nameChange = entry.changes.find(c => c.key === 'name');
                    if (nameChange && nameChange.old) {
                        await targetGuild.setName(nameChange.old, 'Watchlist anti-nuke: Reverted unauthorized server name edit').catch(() => {});
                        console.warn(`[REVERT SUCCESS] Reverted server name back to: "${nameChange.old}"`);
                    }
                }
            }
        };

        await processWatchlistStrike(targetGuild, executorId, category, actionName, targetName, extraRevertAction);
    } catch (err) {
        console.error('[Audit Log Watcher Error]:', err.message);
    }
});

// 3. Fallback Member Ban Listener
client.on('guildBanAdd', async (ban) => {
    try {
        const fetchedLogs = await ban.guild.fetchAuditLogs({ limit: 1, type: AuditLogEvent.MemberBanAdd }).catch(() => null);
        if (!fetchedLogs) return;
        const banLog = fetchedLogs.entries.first();
        if (!banLog) return;
        const executorId = banLog.executorId || banLog.executor?.id;
        if (executorId && isWatchedUser(executorId, ban.guild)) {
            await processWatchlistStrike(ban.guild, executorId, 'MODERATION', 'MemberBanAdd', ban.user.tag);
        }
    } catch (err) {
        console.error('[Ban Listener Error]:', err.message);
    }
});

// 4. Fallback Member Timeout Listener
client.on('guildMemberUpdate', async (oldMember, newMember) => {
    try {
        const oldTimeout = oldMember.communicationDisabledUntilTimestamp || 0;
        const newTimeout = newMember.communicationDisabledUntilTimestamp || 0;

        if (newTimeout > Date.now() && newTimeout !== oldTimeout) {
            // Member was timed out! Fetch audit log executor
            const fetchedLogs = await newMember.guild.fetchAuditLogs({ limit: 1, type: AuditLogEvent.MemberUpdate }).catch(() => null);
            if (!fetchedLogs) return;
            const updateLog = fetchedLogs.entries.first();
            if (!updateLog) return;
            const executorId = updateLog.executorId || updateLog.executor?.id;
            if (executorId && isWatchedUser(executorId, newMember.guild)) {
                await processWatchlistStrike(newMember.guild, executorId, 'MODERATION', 'MemberTimeout', newMember.user.tag);
            }
        }
    } catch (err) {
        console.error('[Timeout Listener Error]:', err.message);
    }
});

// 5. Fallback Member Kick Listener
client.on('guildMemberRemove', async (member) => {
    try {
        // Invite Tracker: Mark as left
        const record = invitedMembers.get(member.id);
        if (record && record.inviterId) {
            let invData = userInvites.get(record.inviterId) || { regular: 0, left: 0, fake: 0, bonus: 0 };
            invData.left = (invData.left || 0) + 1;
            userInvites.set(record.inviterId, invData);
            saveData();
        }

        const fetchedLogs = await member.guild.fetchAuditLogs({ limit: 1, type: AuditLogEvent.MemberKick }).catch(() => null);
        if (!fetchedLogs) return;
        const kickLog = fetchedLogs.entries.first();
        if (!kickLog) return;

        // Ensure kick event occurred in the last 5 seconds
        if (Date.now() - kickLog.createdTimestamp > 5000) return;

        const executorId = kickLog.executorId || kickLog.executor?.id;
        if (executorId && isWatchedUser(executorId, member.guild)) {
            await processWatchlistStrike(member.guild, executorId, 'MODERATION', 'MemberKick', member.user.tag);
        }
    } catch (err) {
        console.error('[Kick Listener Error]:', err.message);
    }
});

// 6. Direct Member Join Listener (Welcome Messages + Bot Join Anti-Nuke + Invite Tracking)
client.on('guildMemberAdd', async (member) => {
    try {
        if (!member.user.bot) {
            // Invite Tracking: Compare invite usage
            const cachedMap = guildInvitesCache.get(member.guild.id) || new Map();
            const freshInvites = await member.guild.invites.fetch().catch(() => null);
            let usedInvite = null;

            if (freshInvites) {
                for (const [code, inv] of freshInvites.entries()) {
                    const oldUses = cachedMap.get(code) || 0;
                    if (inv.uses > oldUses) {
                        usedInvite = inv;
                        break;
                    }
                }
                const codeMap = new Map();
                freshInvites.forEach(inv => codeMap.set(inv.code, inv.uses || 0));
                guildInvitesCache.set(member.guild.id, codeMap);
            }

            if (usedInvite && usedInvite.inviter) {
                const inviterId = usedInvite.inviter.id;
                const isFake = (Date.now() - member.user.createdTimestamp) < (7 * 24 * 60 * 60 * 1000); // Created < 7 days ago

                invitedMembers.set(member.id, { inviterId, code: usedInvite.code, isFake });

                let invData = userInvites.get(inviterId) || { regular: 0, left: 0, fake: 0, bonus: 0 };
                if (isFake) {
                    invData.fake = (invData.fake || 0) + 1;
                } else {
                    invData.regular = (invData.regular || 0) + 1;
                }
                userInvites.set(inviterId, invData);
                saveData();
            }

            // 1. Auto-Role Assignment (Per-Guild support)
            const mainGuildId = (process.env.GUILD_ID || '').trim().replace(/^["']|["']$/g, '');
            const customerGuildId = (process.env.DISCORD_CUSTOMER_GUILD_ID || process.env.GUILD_ID_CUSTOMERS || '').trim().replace(/^["']|["']$/g, '');

            let autoRoleId = null;
            if (member.guild.id === mainGuildId) {
                autoRoleId = process.env.DISCORD_MAIN_AUTOROLE_ID || process.env.DISCORD_AUTOROLE_ID;
            } else if (member.guild.id === customerGuildId) {
                autoRoleId = process.env.DISCORD_CUSTOMER_AUTOROLE_ID || process.env.DISCORD_AUTOROLE_ID;
            } else {
                autoRoleId = process.env.DISCORD_AUTOROLE_ID;
            }

            if (autoRoleId) {
                const cleanedRoleId = String(autoRoleId).trim().replace(/^["']|["']$/g, '');
                if (cleanedRoleId) {
                    const role = member.guild.roles.cache.get(cleanedRoleId) || await member.guild.roles.fetch(cleanedRoleId).catch(() => null);
                    if (role) {
                        await member.roles.add(role, 'Auto-role on member join').catch(err => {
                            console.error(`[AutoRole Error] Failed to give role ${role.name} to ${member.user.tag}:`, err.message);
                        });
                        console.log(`[AutoRole] Granted role "${role.name}" to ${member.user.tag} in ${member.guild.name}`);
                    }
                }
            }

            // 2. Send Premium Welcome Embed (PFP + Member Count, No Emojis)
            const welcomeChannelId = (process.env.DISCORD_WELCOME_CHANNEL_ID || process.env.DISCORD_ALERT_CHANNEL_ID || '').trim().replace(/^["']|["']$/g, '');
            let channel = null;
            if (welcomeChannelId) {
                channel = member.guild.channels.cache.get(welcomeChannelId) || await member.guild.channels.fetch(welcomeChannelId).catch(() => null);
            }
            if (!channel && member.guild.systemChannel) {
                channel = member.guild.systemChannel;
            }

            if (channel && channel.isTextBased()) {
                const avatarUrl = member.user.displayAvatarURL({ size: 512, extension: 'png' });
                const memberCount = member.guild.memberCount;
                const createdTimestamp = Math.floor(member.user.createdTimestamp / 1000);

                const embed = new EmbedBuilder()
                    .setTitle('Welcome to Dragonite Client')
                    .setColor(0x5865F2)
                    .setDescription(`Yo what's up <@${member.id}>!`)
                    .setThumbnail(avatarUrl)
                    .addFields(
                        { name: 'Member Count', value: `Member #${memberCount}`, inline: true },
                        { name: 'Account Created', value: `<t:${createdTimestamp}:R>`, inline: true }
                    )
                    .setFooter({ text: 'Dragonite Client', iconURL: member.guild.iconURL() || undefined });

                await channel.send({ embeds: [embed] }).catch(() => {});
            }
            return;
        }

        // Bot Joined — Anti-Nuke Check
        const fetchedLogs = await member.guild.fetchAuditLogs({ limit: 1, type: AuditLogEvent.BotAdd }).catch(() => null);
        if (!fetchedLogs) return;
        const botAddLog = fetchedLogs.entries.first();
        if (!botAddLog) return;

        const executorId = botAddLog.executorId || botAddLog.executor?.id;
        if (executorId && isWatchedUser(executorId, member.guild)) {
            // 1. INSTANTLY KICK & BAN THE NEWLY INVITED BOT
            await member.kick('Watchlist anti-nuke: Unauthorized bot invite by watched developer').catch(() => {});
            await member.guild.bans.create(member.id, { reason: 'Watchlist anti-nuke: Unauthorized bot invite by watched developer' }).catch(() => {});

            console.error(`[ANTI-NUKE] Successfully kicked & banned invited bot: ${member.user.tag} (${member.id})`);

            // 2. INSTANTLY NEUTRALIZE THE DEVELOPER WHO INVITED IT
            await processWatchlistStrike(member.guild, executorId, 'BOT_ADD', 'BotAdd', member.user.tag);
        }
    } catch (err) {
        console.error('[Member Join Error]:', err.message);
    }
});

// 7. Invite Cache Event Listeners
client.on('inviteCreate', invite => {
    if (invite.guild) cacheGuildInvites(invite.guild);
});

client.on('inviteDelete', invite => {
    if (invite.guild) cacheGuildInvites(invite.guild);
});

// 8. Server Lockdown Listener (Auto-Leaves Unauthorized Servers)
client.on('guildCreate', async (guild) => {
    if (guild) cacheGuildInvites(guild);
    const allowedGuildIds = getAllowedGuildIds();
    if (allowedGuildIds.length > 0 && !allowedGuildIds.includes(guild.id)) {
        console.warn(`[SECURITY LOCKDOWN] Bot invited to unauthorized server "${guild.name}" (${guild.id}). Leaving immediately!`);
        await guild.leave().catch(() => {});
    }
});
