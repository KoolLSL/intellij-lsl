@global
-- Auto-generated SLua definitions for luau-lsp
-- Generated from slua_definitions.yaml

export type rotation = quaternion

export type list = {string | number | vector | uuid | quaternion | boolean}

export type LLDetectedEventName = {}

export type LLNonDetectedEventName = {}

export type LLEventName = LLDetectedEventName | LLNonDetectedEventName

export type LLEventHandler = (...any) -> ()

export type LLDetectedEventHandler = (detected: {DetectedEvent}) -> ()

export type LLTimerEveryCallback = (scheduled: number, interval: number) -> ()

export type LLTimerOnceCallback = (scheduled: number) -> ()

export type LLTimerCallback = LLTimerEveryCallback | LLTimerOnceCallback

export type DateTypeArg = {
  year: number,
  month: number,
  day: number,
  hour: number?,
  min: number?,
  sec: number?,
  isdst: boolean?,
}

export type DateTypeResult = {
  year: number,
  month: number,
  day: number,
  hour: number?,
  min: number?,
  sec: number?,
  wday: number?,
  yday: number?,
  isdst: boolean?,
}

export type LLJsonEncodeOptions = {
  tight: boolean?,
  skip_tojson: boolean?,
  allow_sparse: boolean?,
  replacer: ((key: any, value: any, parent: {}?) -> any)?,
}

export type LLJsonDecodeReviverWithoutPath = (key: string | number, value: any, parent: {}?, ctx: {}) -> any

export type LLJsonDecodeOptionsWithoutPath = {
  track_path: false?,
  reviver: LLJsonDecodeReviverWithoutPath?,
} | LLJsonDecodeReviverWithoutPath

export type LLJsonDecodeOptionsWithPath = {
  track_path: true,
  reviver: (key: string | number, value: any, parent: {}?, ctx: {path: {string | number}}) -> any
}

export type LLJsonDecodeOptions = LLJsonDecodeOptionsWithoutPath | LLJsonDecodeOptionsWithPath

export type ParticleParams = {[string]: any}

export type MediaParams = {[string]: any}

export type HttpRequestParams = {[string]: any}

export type quaternion = {
    x: number,
    y: number,
    z: number,
    s: number,
    __add: (self: any, other: quaternion) -> quaternion,
    __sub: (self: any, other: quaternion) -> quaternion,
    __mul: (self: any, other: quaternion) -> quaternion,
    __div: (self: any, other: quaternion) -> quaternion,
    __unm: (self: any) -> quaternion,
    __eq: (self: any, other: quaternion) -> boolean,
    __tostring: (self: any) -> string,
}

export type uuid = {
    istruthy: boolean,
    bytes: string,
    __tostring: (self: any) -> string,
}

export type vector = {
    x: number,
    y: number,
    z: number,
    __add: (self: any, other: vector) -> vector,
    __sub: (self: any, other: vector) -> vector,
    __unm: (self: any) -> vector,
    __mul: (self: any, other: number | vector | quaternion) -> vector,
    __div: (self: any, other: number | vector | quaternion) -> vector,
    __mod: (self: any, other: vector) -> vector,
    __tostring: (self: any) -> string,
}

export type DetectedEvent = {
    index: number,
    valid: boolean,
    canAdjustDamage: boolean,
}

export type LLEvents = {
    on: (self: any, event: LLEventName, callback: LLEventHandler) -> LLEventHandler,
    off: (self: any, event: LLEventName, callback: LLEventHandler) -> boolean,
    once: (self: any, event: LLEventName, callback: LLEventHandler) -> LLEventHandler,
    handlers: (self: any, event: LLEventName) -> {LLEventHandler},
    eventNames: (self: any) -> {string},
}

export type LLTimers = {
    every: (self: any, seconds: number, callback: LLTimerEveryCallback) -> LLTimerCallback,
    once: (self: any, seconds: number, callback: LLTimerOnceCallback) -> LLTimerCallback,
    off: (self: any, callback: LLTimerCallback) -> boolean,
}

export type PrimParamSetterType = {
    new: () -> PrimParamSetterType,
    apply: (self: PrimParamSetterType, link: number?) -> (),
}

declare rotation: typeof(quaternion)
declare LLEvents: LLEvents
declare LLTimers: LLTimers

declare _G: any
declare _VERSION: string

declare function assert<T>(value: T?, errorMessage: string?): T
declare function collectgarbage(option: "collect"?): ()
declare function collectgarbage(option: "count"): number
declare function dangerouslyexecuterequiredmodule(f: (...any) -> ...any): ...any
declare function error<T>(message: T, level: number?): never
declare function gcinfo(): number
declare function getmetatable<T>(obj: T): getmetatable<T>
declare function graphheap(path: string): ()
declare function graphuserheap(path: string): ()
declare function ipairs<V>(tab: {V}): (({V}, number) -> (number?, V), {V}, number)
declare function newproxy(mt: boolean?): any
declare function next<K, V>(t: {[K]: V}, i: K?): (K?, V)
declare function pairs<K, V>(t: {[K]: V}): (({[K]: V}, K?) -> (K?, V), {[K]: V}, K)
declare function pcall<A..., R...>(f: (A...) -> R..., ...: A...): (boolean, R...)
declare function print<T...>(...: T...): ()
declare function rawequal<T1, T2>(a: T1, b: T2): boolean
declare function rawget<K, V>(t: {[K]: V}, k: K): V?
declare function rawlen<K, V>(t: {[K]: V} | string): number
declare function rawset<K, V>(t: {[K]: V}, k: K, v: V): {[K]: V}
declare function require(target: any): any
declare function select<A...>(i: string | number, ...: A...): ...any
declare function setmetatable<T, MT>(t: T, mt: MT): setmetatable<T, MT>
declare function tonumber(value: string? | number, base: number?): number?
declare function toquaternion(value: string? | quaternion): quaternion?
declare function torotation(value: string? | quaternion): quaternion?
declare function tostring<T>(value: T): string
declare function touuid(val: string? | buffer | uuid): uuid?
declare function tovector(val: string? | vector): vector?
declare function type<T>(obj: T): string
declare function typeof<T>(obj: T): string
declare function unpack<V>(tab: {V}, i: number?, j: number?): ...V
declare function xpcall<E, A..., R1..., R2...>(f: (A...) -> R1..., err: (E) -> R2..., ...: A...): (boolean, R1...)

declare bit32: {
    arshift: (val: number, i: number) -> number,
    band: (...: ...number) -> number,
    bnot: (val: number) -> number,
    bor: (...: ...number) -> number,
    bxor: (...: ...number) -> number,
    btest: (...: ...number) -> boolean,
    extract: (src: number, field: number, width: number?) -> number,
    lrotate: (val: number, i: number) -> number,
    lshift: (val: number, i: number) -> number,
    replace: (dst: number, src: number, field: number, width: number?) -> number,
    rrotate: (val: number, i: number) -> number,
    rshift: (val: number, i: number) -> number,
    s32: (val: number) -> number,
    smul: (val1: number, val2: number) -> number,
    countlz: (val: number) -> number,
    countrz: (val: number) -> number,
    byteswap: (val: number) -> number,
}

declare buffer: {
    create: (size: number) -> buffer,
    fromstring: (str: string) -> buffer,
    tostring: (b: buffer) -> string,
    readi8: (b: buffer, offset: number) -> number,
    readu8: (b: buffer, offset: number) -> number,
    readi16: (b: buffer, offset: number) -> number,
    readu16: (b: buffer, offset: number) -> number,
    readi32: (b: buffer, offset: number) -> number,
    readinteger: (b: buffer, offset: number) -> number,
    readu32: (b: buffer, offset: number) -> number,
    readf32: (b: buffer, offset: number) -> number,
    readf64: (b: buffer, offset: number) -> number,
    writei8: (b: buffer, offset: number, value: number) -> (),
    writeu8: (b: buffer, offset: number, value: number) -> (),
    writei16: (b: buffer, offset: number, value: number) -> (),
    writeu16: (b: buffer, offset: number, value: number) -> (),
    writei32: (b: buffer, offset: number, value: number) -> (),
    writeinteger: (b: buffer, offset: number, value: number) -> (),
    writeu32: (b: buffer, offset: number, value: number) -> (),
    writef32: (b: buffer, offset: number, value: number) -> (),
    writef64: (b: buffer, offset: number, value: number) -> (),
    readstring: (b: buffer, offset: number, count: number) -> string,
    writestring: (b: buffer, offset: number, value: string, count: number?) -> (),
    len: (b: buffer) -> number,
    copy: (target: buffer, targetOffset: number, source: buffer, sourceOffset: number?, count: number?) -> (),
    fill: (b: buffer, offset: number, value: number, count: number?) -> (),
    readbits: (b: buffer, bitOffset: number, bitCount: number) -> number,
    writebits: (b: buffer, bitOffset: number, bitCount: number, value: number) -> (),
}

declare coroutine: {
    create: (f: (...any) -> ...any) -> thread,
    resume: (co: thread, ...: ...any) -> (boolean, ...any),
    running: () -> thread?,
    status: (co: thread) -> "running" | "suspended" | "normal" | "dead",
    wrap: (f: (...any) -> ...any) -> (...any) -> ...any,
    yield: (...: ...any) -> ...any,
    isyieldable: () -> boolean,
    close: (co: thread) -> (boolean, string?),
}

declare debug: {
    info: (level: number, s: string) -> ...any,
    info: (func: (...any) -> ...any, s: string) -> ...any,
    traceback: (msg: string?, level: number?) -> string,
}

declare integer: {
    add: (int1: number, int2: number) -> number,
    arshift: (val: number, i: number) -> number,
    band: (...: ...integer) -> number,
    bnot: (val: number) -> number,
    bor: (...: ...integer) -> number,
    bxor: (...: ...integer) -> number,
    bswap: (val: number) -> number,
    btest: (...: ...integer) -> boolean,
    clamp: (val: number, min: number, max: number) -> number,
    countlz: (val: number) -> number,
    countrz: (val: number) -> number,
    create: (val: number) -> integer?,
    div: (dividend: number, divisor: number) -> number,
    extract: (src: number, field: number, width: integer?) -> number,
    fromstring: (value: string, base: number?) -> integer?,
    ge: (int1: number, int2: number) -> boolean,
    gt: (int1: number, int2: number) -> boolean,
    idiv: (dividend: number, divisor: number) -> number,
    le: (int1: number, int2: number) -> boolean,
    lt: (int1: number, int2: number) -> boolean,
    lrotate: (val: number, i: number) -> number,
    lshift: (val: number, i: number) -> number,
    max: (val: number, ...: ...integer) -> number,
    min: (val: number, ...: ...integer) -> number,
    mod: (dividend: number, divisor: number) -> number,
    mul: (int1: number, int2: number) -> number,
    neg: (val: number) -> number,
    rem: (dividend: number, divisor: number) -> number,
    replace: (dst: number, src: number, field: number, width: integer?) -> number,
    rrotate: (val: number, i: number) -> number,
    rshift: (val: number, i: number) -> number,
    sub: (int1: number, int2: number) -> number,
    tonumber: (val: number) -> number,
    udiv: (dividend: number, divisor: number) -> number,
    uge: (int1: number, int2: number) -> boolean,
    ugt: (int1: number, int2: number) -> boolean,
    ule: (int1: number, int2: number) -> boolean,
    ult: (int1: number, int2: number) -> boolean,
    urem: (dividend: number, divisor: number) -> number,
}

declare llbase64: {
    encode: (data: string | buffer) -> string,
    decode: (data: string, asBuffer: true) -> buffer,
}

declare lljson: {
    encode: (value: any, options: LLJsonEncodeOptions?) -> string,
    decode: (json: string, options: LLJsonDecodeOptions?) -> any,
    slencode: (value: any, options: LLJsonEncodeOptions?) -> string,
    sldecode: (json: string, options: LLJsonDecodeOptions?) -> any,
}

declare llprim: {
    setParticleSystem: (params: ParticleParams?, link: number?) -> (),
    setMedia: (face: number, params: MediaParams?, link: number?) -> number,
}

declare math: {
    abs: (val: number) -> number,
    acos: (val: number) -> number,
    asin: (val: number) -> number,
    atan: (val: number) -> number,
    atan2: (y: number, x: number) -> number,
    ceil: (val: number) -> number,
    clamp: (val: number, min: number, max: number) -> number,
    cos: (theta: number) -> number,
    cosh: (val: number) -> number,
    deg: (theta: number) -> number,
    exp: (val: number) -> number,
    floor: (val: number) -> number,
    fmod: (dividend: number, modulus: number) -> number,
    frexp: (val: number) -> (number, number),
    ldexp: (significand: number, exponent: number) -> number,
    lerp: (start_val: number, end_val: number, t: number) -> number,
    log: (val: number, base: number?) -> number,
    log10: (val: number) -> number,
    map: (val: number, inMin: number, inMax: number, outMin: number, outMax: number) -> number,
    max: (val: number, ...: ...number) -> number,
    min: (val: number, ...: ...number) -> number,
    modf: (val: number) -> (number, number),
    noise: (x: number, y: number?, z: number?) -> number,
    pow: (base: number, exponent: number) -> number,
    rad: (theta: number) -> number,
    random: (min: number?, max: number?) -> number,
    randomseed: (seed: number) -> (),
    round: (val: number) -> number,
    sign: (val: number) -> number,
    sin: (theta: number) -> number,
    sinh: (val: number) -> number,
    sqrt: (val: number) -> number,
    tan: (theta: number) -> number,
    tanh: (val: number) -> number,
    isnan: (val: number) -> boolean,
    isinf: (val: number) -> boolean,
    isfinite: (val: number) -> boolean,
}

declare os: {
    clock: () -> number,
    date: (formatString: "*t" | "!*t", t: number?) -> DateTypeResult,
    difftime: (a: number, b: number) -> number,
    time: () -> number,
}

declare quaternion: {
    create: (x: number, y: number, z: number, s: number) -> quaternion,
    normalize: (q: quaternion) -> quaternion,
    magnitude: (q: quaternion) -> number,
    dot: (q1: quaternion, q2: quaternion) -> number,
    slerp: (start_q: quaternion, end_q: quaternion, t: number) -> quaternion,
    conjugate: (q: quaternion) -> quaternion,
    tofwd: (q: quaternion) -> vector,
    toleft: (q: quaternion) -> vector,
    toup: (q: quaternion) -> vector,
}

declare string: {
    byte: (src: string, start_index: number?, end_index: number?) -> ...number,
    char: (...: ...number) -> string,
    find: (src: string, pattern: string, start_index: number?, plain: boolean?) -> (number?, number?, ...string),
    format: (format_string: string, ...: ...any) -> string,
    gmatch: (src: string, pattern: string) -> () -> ...string,
    gsub: (src: string, pattern: string, repl: string | { [string]: string } | (...string) -> string, maxn: number?) -> (string, number),
    len: (str: string) -> number,
    lower: (src: string) -> string,
    match: (src: string, pattern: string, start_index: number?) -> ...string?,
    pack: (format_string: string, ...: ...any) -> string,
    packsize: (format_string: string) -> number,
    rep: (src: string, count: number) -> string,
    reverse: (src: string) -> string,
    split: (src: string, separator: string?) -> {string},
    sub: (src: string, start_index: number, end_index: number?) -> string,
    unpack: (format_string: string, src: string, start: number?) -> ...any,
    upper: (src: string) -> string,
}

declare table: {
    concat: (a: {string | number}, sep: string?, i: number?, j: number?) -> string,
    foreach: (t: {[K]: V}, f: (key: K, value: V) -> R?) -> R?,
    foreachi: (a: {V}, f: (index: number, value: V) -> R?) -> R?,
    getn: (a: {any}) -> number,
    maxn: (t: {any}) -> number,
    insert: (a: {V}, value: V) -> (),
    append: (a: {V}, ...: ...V) -> (),
    extend: (a: {V}, b: {V}) -> {V},
    remove: (a: {V}, i: number?) -> V?,
    sort: (a: {V}, f: ((a: V, b: V) -> boolean)?) -> (),
    pack: (...: ...V) -> { n: number, [number]: V },
    unpack: (a: {V}, i: number?, j: number?) -> ...V,
    move: (src: {V}, i: number, j: number, d: number, dest: {V}?) -> {V},
    create: (n: number, v: V?) -> {V},
    find: (t: {V}, v: V, i: number?) -> number?,
    clear: (t: {}) -> (),
    shrink: (t: {V}, shrink_sparse: boolean?) -> {V},
    freeze: (t: table) -> table,
    isfrozen: (t: {}) -> boolean,
    clone: (t: table) -> table,
}

declare utf8: {
    char: (...: ...number) -> string,
    codes: (src: string) -> ((string, number) -> (number, number), string, number),
    codepoint: (src: string, start_index: number?, end_index: number?) -> ...number,
    len: (str: string, start_index: number?, end_index: number?) -> (number?, number?),
    offset: (str: string, n: number, start_index: number?) -> number?,
}

declare uuid: {
    create: (value: string? | buffer | uuid) -> uuid,
}

declare vector: {
    create: (x: number, y: number, z: number?) -> vector,
    magnitude: (vec: vector) -> number,
    normalize: (vec: vector) -> vector,
    cross: (start_vec: vector, end_vec: vector) -> vector,
    dot: (vec1: vector, vec2: vector) -> number,
    angle: (start_vec: vector, end_vec: vector, axis: vector?) -> number,
    floor: (vec: vector) -> vector,
    ceil: (vec: vector) -> vector,
    abs: (vec: vector) -> vector,
    sign: (vec: vector) -> vector,
    clamp: (vec: vector, min: vector, max: vector) -> vector,
    max: (vec: vector, ...: ...vector) -> vector,
    min: (vec: vector, ...: ...vector) -> vector,
    lerp: (start_vec: vector, end_vec: vector, t: number) -> vector,
}

declare ll: {
}

declare llcompat: {
}

