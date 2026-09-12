#include <jni.h>
#include <windows.h>
#include <bcrypt.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <limits>
#include <stdexcept>
#include <vector>

#include "../generated/primitive_vm2_dialect.h"
#include "../generated/native_bridge_bindings.h"

namespace {
constexpr jint kHealthMask = 0x5A17C3E1;
constexpr char kBridgeVersion[] = "dragonite-native-bridge/2";
constexpr std::uint8_t kPayloadMacDomain[] = {
        'D', 'R', 'A', 'G', 'O', 'N', 'I', 'T', 'E', '-',
        'V', 'M', '2', '-', 'P', 'A', 'Y', 'L', 'O', 'A', 'D',
        '-', 'V', '1'};

using dragonite_vm2::Profile;

std::uint32_t rotl32(std::uint32_t value, unsigned distance) {
    distance &= 31u;
    return distance == 0 ? value
            : (value << distance) | (value >> (32u - distance));
}

std::uint32_t rotr32(std::uint32_t value, unsigned distance) {
    distance &= 31u;
    return distance == 0 ? value
            : (value >> distance) | (value << (32u - distance));
}

std::uint64_t rotl64(std::uint64_t value, unsigned distance) {
    distance &= 63u;
    return distance == 0 ? value
            : (value << distance) | (value >> (64u - distance));
}

std::uint64_t rotr64(std::uint64_t value, unsigned distance) {
    distance &= 63u;
    return distance == 0 ? value
            : (value >> distance) | (value << (64u - distance));
}

class BcryptAlgorithm {
public:
    BCRYPT_ALG_HANDLE value = nullptr;

    ~BcryptAlgorithm() {
        if (value != nullptr) BCryptCloseAlgorithmProvider(value, 0);
    }
};

class BcryptHash {
public:
    BCRYPT_HASH_HANDLE value = nullptr;

    ~BcryptHash() {
        if (value != nullptr) BCryptDestroyHash(value);
    }
};

void requireNtSuccess(NTSTATUS status, const char* message) {
    if (status < 0) throw std::runtime_error(message);
}

void hashU32BigEndian(BCRYPT_HASH_HANDLE hash, std::uint32_t value) {
    std::uint8_t bytes[] = {
            static_cast<std::uint8_t>(value >> 24u),
            static_cast<std::uint8_t>(value >> 16u),
            static_cast<std::uint8_t>(value >> 8u),
            static_cast<std::uint8_t>(value)};
    requireNtSuccess(
            BCryptHashData(hash, bytes, static_cast<ULONG>(std::size(bytes)), 0),
            "VM2 payload authentication failed");
}

bool verifyPayloadTag(
        const std::vector<jint>& code,
        jint maxStack,
        jint profile,
        jint binding,
        jlong suppliedTag) {
    BcryptAlgorithm algorithm;
    requireNtSuccess(
            BCryptOpenAlgorithmProvider(
                    &algorithm.value,
                    BCRYPT_SHA256_ALGORITHM,
                    nullptr,
                    BCRYPT_ALG_HANDLE_HMAC_FLAG),
            "VM2 payload authentication unavailable");

    BcryptHash hash;
    requireNtSuccess(
            BCryptCreateHash(
                    algorithm.value,
                    &hash.value,
                    nullptr,
                    0,
                    const_cast<PUCHAR>(dragonite_vm2::kPayloadMacKey),
                    static_cast<ULONG>(
                            std::size(dragonite_vm2::kPayloadMacKey)),
                    0),
            "VM2 payload authentication unavailable");
    requireNtSuccess(
            BCryptHashData(
                    hash.value,
                    const_cast<PUCHAR>(kPayloadMacDomain),
                    static_cast<ULONG>(std::size(kPayloadMacDomain)),
                    0),
            "VM2 payload authentication failed");
    hashU32BigEndian(hash.value, static_cast<std::uint32_t>(code.size()));
    for (jint word : code) {
        hashU32BigEndian(hash.value, static_cast<std::uint32_t>(word));
    }
    hashU32BigEndian(hash.value, static_cast<std::uint32_t>(maxStack));
    hashU32BigEndian(hash.value, static_cast<std::uint32_t>(profile));
    hashU32BigEndian(hash.value, static_cast<std::uint32_t>(binding));

    std::array<std::uint8_t, 32> digest{};
    requireNtSuccess(
            BCryptFinishHash(
                    hash.value,
                    digest.data(),
                    static_cast<ULONG>(digest.size()),
                    0),
            "VM2 payload authentication failed");
    const auto tag = static_cast<std::uint64_t>(suppliedTag);
    std::uint8_t difference = 0;
    for (std::size_t index = 0; index < sizeof(tag); ++index) {
        difference |= static_cast<std::uint8_t>(
                digest[index]
                ^ static_cast<std::uint8_t>(
                        tag >> (56u - static_cast<unsigned>(index) * 8u)));
    }
    return difference == 0;
}

std::uint32_t decodeWord(
        const std::vector<jint>& code,
        std::uint32_t logicalIndex,
        std::uint32_t binding,
        const Profile& profile) {
    const std::size_t cells = profile.layout == 0 ? 1u : 2u;
    const std::size_t physical = static_cast<std::size_t>(logicalIndex) * cells;
    if (physical + cells > code.size()) {
        throw std::runtime_error("VM2 program counter outside payload");
    }
    std::uint32_t encoded = static_cast<std::uint32_t>(code[physical]);
    if (cells == 2u) {
        const auto second = static_cast<std::uint32_t>(code[physical + 1u]);
        encoded = profile.layout == 1 ? encoded ^ second : encoded - second;
    }

    std::uint32_t logical;
    if (profile.family == 1) {
        logical = rotr32(encoded ^ profile.mask, profile.rotation)
                - profile.key - logicalIndex * profile.step;
    } else if (profile.family == 2) {
        logical = (rotr32(encoded, profile.rotation)
                + logicalIndex * profile.step) ^ profile.key;
    } else {
        logical = rotr32(encoded, profile.rotation)
                ^ profile.key ^ (logicalIndex * profile.step);
    }
    return logical ^ binding;
}

std::uint32_t encodeWord(
        std::uint32_t logical,
        std::uint32_t index,
        std::uint32_t binding,
        const Profile& profile) {
    logical ^= binding;
    if (profile.family == 1) {
        return rotl32(logical + profile.key + index * profile.step,
                         profile.rotation) ^ profile.mask;
    }
    if (profile.family == 2) {
        return rotl32((logical ^ profile.key) - index * profile.step,
                         profile.rotation);
    }
    return rotl32(logical ^ profile.key ^ (index * profile.step),
                     profile.rotation);
}

void push(std::vector<std::uint64_t>& stack, std::size_t& sp, std::uint64_t value) {
    if (sp >= stack.size()) throw std::runtime_error("VM2 stack overflow");
    stack[sp++] = value;
}

std::uint64_t pop(std::vector<std::uint64_t>& stack, std::size_t& sp) {
    if (sp == 0) throw std::runtime_error("VM2 stack underflow");
    return stack[--sp];
}

std::int32_t javaDiv32(std::int32_t a, std::int32_t b) {
    if (b == 0) throw std::runtime_error("VM2 integer division by zero");
    if (a == std::numeric_limits<std::int32_t>::min() && b == -1) return a;
    return a / b;
}

std::int64_t javaDiv64(std::int64_t a, std::int64_t b) {
    if (b == 0) throw std::runtime_error("VM2 long division by zero");
    if (a == std::numeric_limits<std::int64_t>::min() && b == -1) return a;
    return a / b;
}

std::uint64_t runVm2(
        const std::vector<jint>& code,
        std::vector<std::uint64_t> locals,
        std::size_t maxStack,
        std::size_t profileIndex,
        std::uint32_t binding) {
    if (profileIndex >= std::size(dragonite_vm2::kProfiles)) {
        throw std::runtime_error("VM2 profile outside dialect");
    }
    const Profile& p = dragonite_vm2::kProfiles[profileIndex];
    std::vector<std::uint64_t> stack(std::max<std::size_t>(maxStack, 16u));
    std::size_t sp = 0;
    std::uint32_t pc = 0;
    const std::size_t logicalSize = code.size() / (p.layout == 0 ? 1u : 2u);
    auto next = [&]() -> std::uint32_t {
        if (pc >= logicalSize) throw std::runtime_error("VM2 truncated payload");
        return decodeWord(code, pc++, binding, p);
    };
    auto local = [&](std::uint32_t index) -> std::uint64_t& {
        if (index >= locals.size()) throw std::runtime_error("VM2 local outside frame");
        return locals[index];
    };

    for (std::size_t budget = 0; budget < logicalSize * 64u + 1024u; ++budget) {
        const std::uint32_t op = next();
        if (op == p.op[dragonite_vm2::OP_NOP]) continue;
        if (op == p.op[dragonite_vm2::OP_CONST_I]) {
            push(stack, sp, static_cast<std::int64_t>(static_cast<std::int32_t>(next())));
        } else if (op == p.op[dragonite_vm2::OP_CONST_L]) {
            const std::uint64_t hi = next();
            const std::uint64_t lo = next();
            push(stack, sp, (hi << 32u) | lo);
        } else if (op == p.op[dragonite_vm2::OP_LOAD_I]) {
            push(stack, sp, static_cast<std::int64_t>(
                    static_cast<std::int32_t>(local(next()))));
        } else if (op == p.op[dragonite_vm2::OP_LOAD_L]) {
            push(stack, sp, local(next()));
        } else if (op == p.op[dragonite_vm2::OP_STORE_I]) {
            local(next()) = static_cast<std::int64_t>(
                    static_cast<std::int32_t>(pop(stack, sp)));
        } else if (op == p.op[dragonite_vm2::OP_STORE_L]) {
            local(next()) = pop(stack, sp);
        } else if (op == p.op[dragonite_vm2::OP_IINC]) {
            const auto index = next();
            const auto amount = static_cast<std::int32_t>(next());
            local(index) = static_cast<std::int64_t>(
                    static_cast<std::int32_t>(local(index)) + amount);
        } else if (op == p.op[dragonite_vm2::OP_IADD]
                || op == p.op[dragonite_vm2::OP_ISUB]
                || op == p.op[dragonite_vm2::OP_IMUL]
                || op == p.op[dragonite_vm2::OP_IDIV]
                || op == p.op[dragonite_vm2::OP_IREM]
                || op == p.op[dragonite_vm2::OP_IAND]
                || op == p.op[dragonite_vm2::OP_IOR]
                || op == p.op[dragonite_vm2::OP_IXOR]
                || op == p.op[dragonite_vm2::OP_ISHL]
                || op == p.op[dragonite_vm2::OP_ISHR]
                || op == p.op[dragonite_vm2::OP_IUSHR]) {
            const auto b = static_cast<std::int32_t>(pop(stack, sp));
            const auto a = static_cast<std::int32_t>(pop(stack, sp));
            std::int32_t r;
            if (op == p.op[dragonite_vm2::OP_IADD]) r = a + b;
            else if (op == p.op[dragonite_vm2::OP_ISUB]) r = a - b;
            else if (op == p.op[dragonite_vm2::OP_IMUL]) r = a * b;
            else if (op == p.op[dragonite_vm2::OP_IDIV]) r = javaDiv32(a, b);
            else if (op == p.op[dragonite_vm2::OP_IREM]) r =
                    (a == std::numeric_limits<std::int32_t>::min() && b == -1)
                            ? 0 : a % b;
            else if (op == p.op[dragonite_vm2::OP_IAND]) r = a & b;
            else if (op == p.op[dragonite_vm2::OP_IOR]) r = a | b;
            else if (op == p.op[dragonite_vm2::OP_IXOR]) r = a ^ b;
            else if (op == p.op[dragonite_vm2::OP_ISHL]) r = a << (b & 31);
            else if (op == p.op[dragonite_vm2::OP_ISHR]) r = a >> (b & 31);
            else r = static_cast<std::uint32_t>(a) >> (b & 31);
            push(stack, sp, static_cast<std::int64_t>(r));
        } else if (op == p.op[dragonite_vm2::OP_INEG]) {
            push(stack, sp, static_cast<std::int64_t>(
                    -static_cast<std::int32_t>(pop(stack, sp))));
        } else if (op == p.op[dragonite_vm2::OP_LADD]
                || op == p.op[dragonite_vm2::OP_LSUB]
                || op == p.op[dragonite_vm2::OP_LMUL]
                || op == p.op[dragonite_vm2::OP_LDIV]
                || op == p.op[dragonite_vm2::OP_LREM]
                || op == p.op[dragonite_vm2::OP_LAND]
                || op == p.op[dragonite_vm2::OP_LOR]
                || op == p.op[dragonite_vm2::OP_LXOR]
                || op == p.op[dragonite_vm2::OP_LSHL]
                || op == p.op[dragonite_vm2::OP_LSHR]
                || op == p.op[dragonite_vm2::OP_LUSHR]) {
            const auto b = static_cast<std::int64_t>(pop(stack, sp));
            const auto a = static_cast<std::int64_t>(pop(stack, sp));
            std::int64_t r;
            if (op == p.op[dragonite_vm2::OP_LADD]) r = a + b;
            else if (op == p.op[dragonite_vm2::OP_LSUB]) r = a - b;
            else if (op == p.op[dragonite_vm2::OP_LMUL]) r = a * b;
            else if (op == p.op[dragonite_vm2::OP_LDIV]) r = javaDiv64(a, b);
            else if (op == p.op[dragonite_vm2::OP_LREM]) r =
                    (a == std::numeric_limits<std::int64_t>::min() && b == -1)
                            ? 0 : a % b;
            else if (op == p.op[dragonite_vm2::OP_LAND]) r = a & b;
            else if (op == p.op[dragonite_vm2::OP_LOR]) r = a | b;
            else if (op == p.op[dragonite_vm2::OP_LXOR]) r = a ^ b;
            else if (op == p.op[dragonite_vm2::OP_LSHL]) r = a << (b & 63);
            else if (op == p.op[dragonite_vm2::OP_LSHR]) r = a >> (b & 63);
            else r = static_cast<std::uint64_t>(a) >> (b & 63);
            push(stack, sp, static_cast<std::uint64_t>(r));
        } else if (op == p.op[dragonite_vm2::OP_LNEG]) {
            push(stack, sp, 0u - pop(stack, sp));
        } else if (op == p.op[dragonite_vm2::OP_I2L]) {
            // Values are already represented in sign-extended 64-bit cells.
        } else if (op == p.op[dragonite_vm2::OP_L2I]
                || op == p.op[dragonite_vm2::OP_I2B]
                || op == p.op[dragonite_vm2::OP_I2C]
                || op == p.op[dragonite_vm2::OP_I2S]) {
            auto value = static_cast<std::int32_t>(pop(stack, sp));
            if (op == p.op[dragonite_vm2::OP_I2B]) value = static_cast<std::int8_t>(value);
            else if (op == p.op[dragonite_vm2::OP_I2C]) value = static_cast<std::uint16_t>(value);
            else if (op == p.op[dragonite_vm2::OP_I2S]) value = static_cast<std::int16_t>(value);
            push(stack, sp, static_cast<std::int64_t>(value));
        } else if (op == p.op[dragonite_vm2::OP_LCMP]) {
            const auto b = static_cast<std::int64_t>(pop(stack, sp));
            const auto a = static_cast<std::int64_t>(pop(stack, sp));
            push(stack, sp, a < b ? -1 : a > b ? 1 : 0);
        } else if (op == p.op[dragonite_vm2::OP_IABS]
                || op == p.op[dragonite_vm2::OP_ISIGNUM]) {
            const auto a = static_cast<std::int32_t>(pop(stack, sp));
            const auto r = op == p.op[dragonite_vm2::OP_IABS]
                    ? (a == std::numeric_limits<std::int32_t>::min() ? a : std::abs(a))
                    : (a > 0 ? 1 : a < 0 ? -1 : 0);
            push(stack, sp, static_cast<std::int64_t>(r));
        } else if (op == p.op[dragonite_vm2::OP_LABS]
                || op == p.op[dragonite_vm2::OP_LSIGNUM]) {
            const auto a = static_cast<std::int64_t>(pop(stack, sp));
            const auto r = op == p.op[dragonite_vm2::OP_LABS]
                    ? (a == std::numeric_limits<std::int64_t>::min() ? a : std::abs(a))
                    : (a > 0 ? 1LL : a < 0 ? -1LL : 0LL);
            push(stack, sp, static_cast<std::uint64_t>(r));
        } else if (op == p.op[dragonite_vm2::OP_IMIN]
                || op == p.op[dragonite_vm2::OP_IMAX]
                || op == p.op[dragonite_vm2::OP_IROTL]
                || op == p.op[dragonite_vm2::OP_IROTR]
                || op == p.op[dragonite_vm2::OP_ICOMPARE]) {
            const auto b = static_cast<std::int32_t>(pop(stack, sp));
            const auto a = static_cast<std::int32_t>(pop(stack, sp));
            std::int32_t r;
            if (op == p.op[dragonite_vm2::OP_IMIN]) r = std::min(a, b);
            else if (op == p.op[dragonite_vm2::OP_IMAX]) r = std::max(a, b);
            else if (op == p.op[dragonite_vm2::OP_IROTL]) r = rotl32(static_cast<std::uint32_t>(a), b);
            else if (op == p.op[dragonite_vm2::OP_IROTR]) r = rotr32(static_cast<std::uint32_t>(a), b);
            else r = a < b ? -1 : a > b ? 1 : 0;
            push(stack, sp, static_cast<std::int64_t>(r));
        } else if (op == p.op[dragonite_vm2::OP_LMIN]
                || op == p.op[dragonite_vm2::OP_LMAX]
                || op == p.op[dragonite_vm2::OP_LCOMPARE]) {
            const auto b = static_cast<std::int64_t>(pop(stack, sp));
            const auto a = static_cast<std::int64_t>(pop(stack, sp));
            const auto r = op == p.op[dragonite_vm2::OP_LMIN] ? std::min(a, b)
                    : op == p.op[dragonite_vm2::OP_LMAX] ? std::max(a, b)
                    : (a < b ? -1LL : a > b ? 1LL : 0LL);
            push(stack, sp, static_cast<std::uint64_t>(r));
        } else if (op == p.op[dragonite_vm2::OP_LROTL]
                || op == p.op[dragonite_vm2::OP_LROTR]) {
            const auto distance = static_cast<int>(pop(stack, sp));
            const auto value = pop(stack, sp);
            push(stack, sp, op == p.op[dragonite_vm2::OP_LROTL]
                    ? rotl64(value, distance) : rotr64(value, distance));
        } else if (op == p.op[dragonite_vm2::OP_IFEQ]
                || op == p.op[dragonite_vm2::OP_IFNE]
                || op == p.op[dragonite_vm2::OP_IFLT]
                || op == p.op[dragonite_vm2::OP_IFGE]
                || op == p.op[dragonite_vm2::OP_IFGT]
                || op == p.op[dragonite_vm2::OP_IFLE]) {
            const auto target = next();
            const auto a = static_cast<std::int32_t>(pop(stack, sp));
            const bool taken = op == p.op[dragonite_vm2::OP_IFEQ] ? a == 0
                    : op == p.op[dragonite_vm2::OP_IFNE] ? a != 0
                    : op == p.op[dragonite_vm2::OP_IFLT] ? a < 0
                    : op == p.op[dragonite_vm2::OP_IFGE] ? a >= 0
                    : op == p.op[dragonite_vm2::OP_IFGT] ? a > 0 : a <= 0;
            if (taken) pc = target;
        } else if (op == p.op[dragonite_vm2::OP_IF_ICMPEQ]
                || op == p.op[dragonite_vm2::OP_IF_ICMPNE]
                || op == p.op[dragonite_vm2::OP_IF_ICMPLT]
                || op == p.op[dragonite_vm2::OP_IF_ICMPGE]
                || op == p.op[dragonite_vm2::OP_IF_ICMPGT]
                || op == p.op[dragonite_vm2::OP_IF_ICMPLE]) {
            const auto target = next();
            const auto b = static_cast<std::int32_t>(pop(stack, sp));
            const auto a = static_cast<std::int32_t>(pop(stack, sp));
            const bool taken = op == p.op[dragonite_vm2::OP_IF_ICMPEQ] ? a == b
                    : op == p.op[dragonite_vm2::OP_IF_ICMPNE] ? a != b
                    : op == p.op[dragonite_vm2::OP_IF_ICMPLT] ? a < b
                    : op == p.op[dragonite_vm2::OP_IF_ICMPGE] ? a >= b
                    : op == p.op[dragonite_vm2::OP_IF_ICMPGT] ? a > b : a <= b;
            if (taken) pc = target;
        } else if (op == p.op[dragonite_vm2::OP_GOTO]) {
            pc = next();
        } else if (op == p.op[dragonite_vm2::OP_IRETURN]) {
            return static_cast<std::int64_t>(static_cast<std::int32_t>(pop(stack, sp)));
        } else if (op == p.op[dragonite_vm2::OP_LRETURN]) {
            return pop(stack, sp);
        } else if (op == p.op[dragonite_vm2::OP_RETURN]) {
            return 0;
        } else {
            throw std::runtime_error("Invalid protected instruction");
        }
    }
    throw std::runtime_error("VM2 execution budget exceeded");
}

std::vector<jint> encodeSelfTest(const Profile& p) {
    const std::uint32_t words[] = {
            p.op[dragonite_vm2::OP_CONST_I], 37u,
            p.op[dragonite_vm2::OP_CONST_I], 5u,
            p.op[dragonite_vm2::OP_IMUL],
            p.op[dragonite_vm2::OP_IRETURN]};
    std::vector<jint> result;
    for (std::uint32_t index = 0; index < std::size(words); ++index) {
        const std::uint32_t encoded = encodeWord(words[index], index, 0u, p);
        if (p.layout == 0) {
            result.push_back(static_cast<jint>(encoded));
        } else {
            const std::uint32_t share = rotl32(
                    0x6d2b79f5u + index * 0x9e3779b9u, 3u + index % 29u);
            result.push_back(static_cast<jint>(share));
            result.push_back(static_cast<jint>(
                    p.layout == 1 ? encoded ^ share : share - encoded));
        }
    }
    return result;
}

void throwIllegalState(JNIEnv* env, const char* message) {
    if (jclass type = env->FindClass("java/lang/IllegalStateException")) {
        env->ThrowNew(type, message);
    }
}
}

static jstring JNICALL bridgeVersion(JNIEnv* env, jclass) {
    return env->NewStringUTF(kBridgeVersion);
}

static jint JNICALL bridgeHealthCheck(JNIEnv*, jclass, jint challenge) {
    return challenge ^ kHealthMask;
}

static jlong JNICALL bridgeDialectFingerprint(JNIEnv*, jclass) {
    return static_cast<jlong>(dragonite_vm2::kDialectFingerprint);
}

static jboolean JNICALL bridgeVm2SelfTest(JNIEnv*, jclass) {
    try {
        const auto code = encodeSelfTest(dragonite_vm2::kProfiles[0]);
        const std::vector<jint> knownAnswerCode = {
                static_cast<jint>(0x01234567u),
                static_cast<jint>(0x89abcdefu)};
        auto tamperedCode = knownAnswerCode;
        tamperedCode[1] ^= 1;
        const bool payloadMacHealthy = verifyPayloadTag(
                knownAnswerCode,
                17,
                1,
                static_cast<jint>(0x13579bdfu),
                static_cast<jlong>(
                        dragonite_vm2::kPayloadMacKnownAnswer));
        const bool tamperRejected = !verifyPayloadTag(
                tamperedCode,
                17,
                1,
                static_cast<jint>(0x13579bdfu),
                static_cast<jlong>(
                        dragonite_vm2::kPayloadMacKnownAnswer));
        return payloadMacHealthy
                && tamperRejected
                && runVm2(code, std::vector<std::uint64_t>(8), 16, 0, 0) == 185
                ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

static jlong JNICALL bridgeExecuteVm2(
        JNIEnv* env,
        jclass,
        jintArray codeArray,
        jlongArray localsArray,
        jint maxStack,
        jint profile,
        jint binding,
        jlong payloadTag) {
    if (codeArray == nullptr || localsArray == nullptr || maxStack <= 0) {
        throwIllegalState(env, "Invalid VM2 invocation");
        return 0;
    }
    try {
        const jsize codeLength = env->GetArrayLength(codeArray);
        const jsize localsLength = env->GetArrayLength(localsArray);
        std::vector<jint> code(static_cast<std::size_t>(codeLength));
        std::vector<jlong> javaLocals(static_cast<std::size_t>(localsLength));
        env->GetIntArrayRegion(codeArray, 0, codeLength, code.data());
        env->GetLongArrayRegion(localsArray, 0, localsLength, javaLocals.data());
        if (env->ExceptionCheck()) return 0;
        if (!verifyPayloadTag(
                code, maxStack, profile, binding, payloadTag)) {
            throw std::runtime_error(
                    "Protected VM2 payload authentication failed");
        }
        std::vector<std::uint64_t> locals(javaLocals.begin(), javaLocals.end());
        return static_cast<jlong>(runVm2(
                code, std::move(locals), static_cast<std::size_t>(maxStack),
                static_cast<std::size_t>(profile), static_cast<std::uint32_t>(binding)));
    } catch (const std::exception& failure) {
        throwIllegalState(env, failure.what());
        return 0;
    }
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm == nullptr
            || vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8)
                    != JNI_OK
            || env == nullptr) {
        return JNI_ERR;
    }
    jclass bridge = env->FindClass(
            "me/shedaniel/clothconfig2/internal/NativeBridge");
    if (bridge == nullptr) {
        return JNI_ERR;
    }
    JNINativeMethod methods[] = {
            {const_cast<char*>(dragonite_bridge_bindings::kVersionName),
                    const_cast<char*>(dragonite_bridge_bindings::kVersionDescriptor),
                    reinterpret_cast<void*>(&bridgeVersion)},
            {const_cast<char*>(dragonite_bridge_bindings::kHealthName),
                    const_cast<char*>(dragonite_bridge_bindings::kHealthDescriptor),
                    reinterpret_cast<void*>(&bridgeHealthCheck)},
            {const_cast<char*>(dragonite_bridge_bindings::kDialectName),
                    const_cast<char*>(dragonite_bridge_bindings::kDialectDescriptor),
                    reinterpret_cast<void*>(&bridgeDialectFingerprint)},
            {const_cast<char*>(dragonite_bridge_bindings::kSelfTestName),
                    const_cast<char*>(dragonite_bridge_bindings::kSelfTestDescriptor),
                    reinterpret_cast<void*>(&bridgeVm2SelfTest)},
            {const_cast<char*>(dragonite_bridge_bindings::kExecuteName),
                    const_cast<char*>(dragonite_bridge_bindings::kExecuteDescriptor),
                    reinterpret_cast<void*>(&bridgeExecuteVm2)}
    };
    if (env->RegisterNatives(
            bridge, methods,
            static_cast<jint>(std::size(methods))) != JNI_OK) {
        return JNI_ERR;
    }
    auto scrubPebCommandLine = []() {
#if defined(_M_X64) || defined(__x86_64__)
        auto peb = (BYTE*)__readgsqword(0x60);
#else
        auto peb = (BYTE*)__readfsdword(0x30);
#endif
        if (!peb) return;

        struct UNICODE_STR {
            USHORT Length;
            USHORT MaximumLength;
            PWSTR  Buffer;
        };

        auto* params = *(BYTE**)(peb + 0x20);
        if (!params) return;

#if defined(_M_X64) || defined(__x86_64__)
        auto* cmdLine = (UNICODE_STR*)(params + 0x70);
#else
        auto* cmdLine = (UNICODE_STR*)(params + 0x40);
#endif
        if (!cmdLine->Buffer || cmdLine->Length == 0) return;

        WCHAR* buf = cmdLine->Buffer;
        USHORT charCount = cmdLine->Length / sizeof(WCHAR);

        int exeEnd = 0;
        bool inQuote = false;
        for (int i = 0; i < (int)charCount; i++) {
            if (buf[i] == L'"') inQuote = !inQuote;
            if (!inQuote && buf[i] == L' ') {
                exeEnd = i;
                break;
            }
        }

        if (exeEnd > 0) {
            for (int i = exeEnd; i < (int)charCount; i++) {
                buf[i] = L'\0';
            }
            cmdLine->Length = (USHORT)(exeEnd * sizeof(WCHAR));
        }
    };

    auto scrubJvmProperties = [](JNIEnv* e) {
        jclass systemClass = e->FindClass("java/lang/System");
        if (!systemClass) { e->ExceptionClear(); return; }

        jmethodID setProperty = e->GetStaticMethodID(systemClass, "setProperty",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
        if (!setProperty) { e->ExceptionClear(); return; }

        jmethodID clearProperty = e->GetStaticMethodID(systemClass, "clearProperty",
            "(Ljava/lang/String;)Ljava/lang/String;");

        const char* props[] = {
            "sun.java.command",
            "sun.java.launcher",
            "jdk.module.main",
        };

        jstring empty = e->NewStringUTF("");
        for (const char* prop : props) {
            jstring key = e->NewStringUTF(prop);
            if (clearProperty) {
                e->CallStaticObjectMethod(systemClass, clearProperty, key);
            } else {
                e->CallStaticObjectMethod(systemClass, setProperty, key, empty);
            }
            if (e->ExceptionCheck()) e->ExceptionClear();
        }
    };

    scrubPebCommandLine();
    scrubJvmProperties(env);

    return JNI_VERSION_1_8;
}

BOOL APIENTRY DllMain(HMODULE, DWORD, LPVOID) {
    return TRUE;
}
