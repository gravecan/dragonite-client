/**
 * Persistent sessions — SQLite (better-sqlite3) with JSON file fallback.
 */
const fs = require('fs');
const path = require('path');

const DATA_DIR = path.join(__dirname, 'data');
const JSON_FILE = path.join(DATA_DIR, 'sessions.json');
const SQLITE_FILE = path.join(DATA_DIR, 'sessions.sqlite');

let db = null;
let useSqlite = false;

function ensureDataDir() {
    if (!fs.existsSync(DATA_DIR)) {
        fs.mkdirSync(DATA_DIR, { recursive: true });
    }
}

function initStore() {
    ensureDataDir();
    try {
        const Database = require('better-sqlite3');
        db = new Database(SQLITE_FILE);
        db.exec(`
            CREATE TABLE IF NOT EXISTS sessions (
                token TEXT PRIMARY KEY,
                data TEXT NOT NULL,
                expires_at TEXT NOT NULL
            );
        `);
        useSqlite = true;
        console.log('[Auth] Session store: SQLite', SQLITE_FILE);
    } catch (e) {
        useSqlite = false;
        db = null;
        if (!fs.existsSync(JSON_FILE)) {
            fs.writeFileSync(JSON_FILE, '{}', 'utf8');
        }
        console.log('[Auth] Session store: JSON fallback', JSON_FILE, `(${e.message})`);
    }
}

function writeJsonAtomic(filePath, obj) {
    const tmp = `${filePath}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(obj, null, 2), 'utf8');
    fs.renameSync(tmp, filePath);
}

function loadInto(sessionsMap) {
    const now = new Date();
    if (useSqlite && db) {
        const rows = db.prepare('SELECT token, data, expires_at FROM sessions').all();
        for (const row of rows) {
            if (new Date(row.expires_at) < now) {
                db.prepare('DELETE FROM sessions WHERE token = ?').run(row.token);
                continue;
            }
            try {
                sessionsMap.set(row.token, JSON.parse(row.data));
            } catch {
                db.prepare('DELETE FROM sessions WHERE token = ?').run(row.token);
            }
        }
        return;
    }
    try {
        const raw = fs.readFileSync(JSON_FILE, 'utf8');
        const obj = JSON.parse(raw);
        for (const [token, data] of Object.entries(obj)) {
            if (!data || !data.expiresAt || new Date(data.expiresAt) < now) {
                continue;
            }
            sessionsMap.set(token, data);
        }
    } catch {
        // empty
    }
}

function upsert(token, session) {
    if (!token || !session) return;
    if (useSqlite && db) {
        db.prepare(`
            INSERT INTO sessions (token, data, expires_at) VALUES (?, ?, ?)
            ON CONFLICT(token) DO UPDATE SET data = excluded.data, expires_at = excluded.expires_at
        `).run(token, JSON.stringify(session), session.expiresAt || new Date(0).toISOString());
        return;
    }
    let obj = {};
    try {
        obj = JSON.parse(fs.readFileSync(JSON_FILE, 'utf8'));
    } catch {
        obj = {};
    }
    obj[token] = session;
    writeJsonAtomic(JSON_FILE, obj);
}

function remove(token) {
    if (!token) return;
    if (useSqlite && db) {
        db.prepare('DELETE FROM sessions WHERE token = ?').run(token);
        return;
    }
    try {
        const obj = JSON.parse(fs.readFileSync(JSON_FILE, 'utf8'));
        delete obj[token];
        writeJsonAtomic(JSON_FILE, obj);
    } catch {
        // ignore
    }
}

function syncAll(sessionsMap) {
    if (useSqlite && db) {
        const tx = db.transaction((entries) => {
            db.prepare('DELETE FROM sessions').run();
            const ins = db.prepare('INSERT INTO sessions (token, data, expires_at) VALUES (?, ?, ?)');
            for (const [token, session] of entries) {
                ins.run(token, JSON.stringify(session), session.expiresAt);
            }
        });
        tx([...sessionsMap.entries()]);
        return;
    }
    const obj = {};
    for (const [token, session] of sessionsMap) {
        obj[token] = session;
    }
    writeJsonAtomic(JSON_FILE, obj);
}

function purgeExpired(sessionsMap) {
    const now = new Date();
    for (const [token, session] of sessionsMap) {
        if (new Date(session.expiresAt) < now) {
            sessionsMap.delete(token);
            remove(token);
        }
    }
}

initStore();

module.exports = {
    loadInto,
    upsert,
    remove,
    syncAll,
    purgeExpired
};
