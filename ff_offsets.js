// Frida runtime offset dumper for Free Fire (IL2CPP)
// Run: frida -U -f com.dts.freefireth -l ff_offsets.js --no-pause
//      (MAX: com.dts.freefiremax)

const m = Process.getModuleByName("libil2cpp.so");
const BASE = m.base;

function fn(name, ret, args) {
    const a = m.findExportByName(name);
    if (a === null) { console.log("[!] missing export: " + name); return null; }
    return new NativeFunction(a, ret, args);
}

const p_domain_get              = fn("il2cpp_domain_get", 'pointer', []);
const p_domain_get_assemblies   = fn("il2cpp_domain_get_assemblies", 'pointer', ['pointer','pointer']);
const p_assembly_get_image      = fn("il2cpp_assembly_get_image", 'pointer', ['pointer']);
const p_image_get_name          = fn("il2cpp_image_get_name", 'pointer', ['pointer']);
const p_image_get_class_count   = fn("il2cpp_image_get_class_count", 'uint32', ['pointer']);
const p_image_get_class         = fn("il2cpp_image_get_class", 'pointer', ['pointer','uint32']);
const p_class_get_name          = fn("il2cpp_class_get_name", 'pointer', ['pointer']);
const p_class_get_namespace     = fn("il2cpp_class_get_namespace", 'pointer', ['pointer']);
const p_class_get_fields        = fn("il2cpp_class_get_fields", 'pointer', ['pointer','pointer']);
const p_field_get_name          = fn("il2cpp_field_get_name", 'pointer', ['pointer']);
const p_field_get_offset        = fn("il2cpp_field_get_offset", 'size_t', ['pointer']);
const p_field_get_type          = fn("il2cpp_field_get_type", 'pointer', ['pointer']);
const p_type_get_name           = fn("il2cpp_type_get_name", 'pointer', ['pointer']);
const p_field_get_flags         = fn("il2cpp_field_get_flags", 'uint32', ['pointer']);
const p_field_static_get_value  = fn("il2cpp_field_static_get_value", 'void', ['pointer','pointer']);

if (!p_domain_get) { console.log("Exports missing -> use Il2CppDumper for offsets."); }

function cstr(p) { return (p && !p.isNull()) ? p.readCString() : ""; }

// Il2CppClass.static_fields offset (adjust if needed, ~0xB8 for Unity 2019+)
const STATIC_FIELDS_OFF = 0xB8;
const FIELD_STATIC_FLAG = 0x10;

const KEYWORDS = ["player","character","camera","avatar","entity","manager","matchgame","gamefacade"];

function dumpClass(klass, imgName) {
    const cname = cstr(p_class_get_name(klass));
    const ns    = cstr(p_class_get_namespace(klass));
    const full  = (ns ? ns + "." : "") + cname;

    let staticFields = null;
    try { staticFields = klass.add(STATIC_FIELDS_OFF).readPointer(); } catch (e) {}

    console.log("\n===== " + full + "  [" + imgName + "] =====");

    const iter = Memory.alloc(Process.pointerSize);
    iter.writePointer(ptr(0));
    let f;
    while (!(f = p_class_get_fields(klass, iter)).isNull()) {
        const fname = cstr(p_field_get_name(f));
        const ftype = cstr(p_type_get_name(p_field_get_type(f)));
        const off   = p_field_get_offset(f).toInt32();
        const flags = p_field_get_flags(f);
        const isStatic = (flags & FIELD_STATIC_FLAG) !== 0;

        if (isStatic) {
            const buf = Memory.alloc(Process.pointerSize);
            p_field_static_get_value(f, buf);
            const val = buf.readPointer();
            let addrStr = "?";
            if (staticFields && !staticFields.isNull()) {
                const fieldAddr = staticFields.add(off);
                addrStr = fieldAddr.sub(BASE).toString(); // libil2cpp offset
            }
            console.log("  [static] " + fname + " : " + ftype +
                        "  val=" + val + "  libOff=" + addrStr);
        } else {
            console.log("  " + fname + " : " + ftype + "  off=0x" + off.toString(16));
        }
    }
}

const domain = p_domain_get();
const cnt = Memory.alloc(8);
const asms = p_domain_get_assemblies(domain, cnt);
const n = cnt.readU32();

for (let i = 0; i < n; i++) {
    const asm = asms.add(i * Process.pointerSize).readPointer();
    const img = p_assembly_get_image(asm);
    const imgName = cstr(p_image_get_name(img));
    if (!imgName) continue;

    const cc = p_image_get_class_count(img);
    for (let j = 0; j < cc; j++) {
        const klass = p_image_get_class(img, j);
        const cname = cstr(p_class_get_name(klass)).toLowerCase();
        if (KEYWORDS.some(k => cname.includes(k)))
            dumpClass(klass, imgName);
    }
}
console.log("\n=== libil2cpp base = " + BASE + " ===");
