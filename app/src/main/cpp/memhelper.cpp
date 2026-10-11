#include <cstdio>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <sys/mman.h>
#include <sys/uio.h>

struct EspEntry { float x, y, headY, dist; int hp, valid; };
struct EspData  {
    volatile int count;
    volatile float aimDx, aimDy;
    int pad[2];
    EspEntry e[64];
};

static int      g_pid = -1;
static EspData* g_out;

static bool rd(uintptr_t a, void* o, size_t n) {
    iovec l{ o, n }, r{ (void*)a, n };
    return process_vm_readv(g_pid, &l, 1, &r, 1, 0) == (ssize_t)n;
}
template<class T> static T R(uintptr_t a) { T v{}; rd(a, &v, sizeof(T)); return v; }

static int findPid(const char* pkg) {
    DIR* d = opendir("/proc"); dirent* e;
    while ((e = readdir(d))) {
        int pid = atoi(e->d_name); if (!pid) continue;
        char p[64]; snprintf(p, 64, "/proc/%d/cmdline", pid);
        int fd = open(p, O_RDONLY); if (fd < 0) continue;
        char b[256] = {0}; read(fd, b, 255); close(fd);
        if (strstr(b, pkg)) { closedir(d); return pid; }
    }
    closedir(d); return -1;
}

static uintptr_t libBase(int pid, const char* lib) {
    char p[64]; snprintf(p, 64, "/proc/%d/maps", pid);
    FILE* f = fopen(p, "r"); char ln[512];
    while (fgets(ln, 512, f))
        if (strstr(ln, lib)) { uintptr_t b = strtoull(ln, 0, 16); fclose(f); return b; }
    fclose(f); return 0;
}

struct Mat4 { float m[16]; };
static bool w2s(const Mat4& vp, float x, float y, float z, int sw, int sh,
                float& sx, float& sy) {
    float cx = vp.m[0]*x + vp.m[4]*y + vp.m[8]*z + vp.m[12];
    float cy = vp.m[1]*x + vp.m[5]*y + vp.m[9]*z + vp.m[13];
    float cw = vp.m[3]*x + vp.m[7]*y + vp.m[11]*z + vp.m[15];
    if (cw < 0.01f) return false;
    sx = ((cx / cw) * 0.5f + 0.5f) * sw;
    sy = (1.0f - ((cy / cw) * 0.5f + 0.5f)) * sh;
    return true;
}

// ============ OFFSETS (fill from Il2CppDumper — see notes) ============
static const uintptr_t O_LIB_BASE_PTR = 0x000000;
static const uintptr_t O_PLAYER_LIST  = 0x00;
static const uintptr_t O_PLAYER_COUNT = 0x00;
static const uintptr_t O_LOCAL_PLAYER = 0x00;
static const uintptr_t O_POS          = 0x00;
static const uintptr_t O_HEAD_BONE    = 0x00;
static const uintptr_t O_HEALTH       = 0x00;
static const uintptr_t O_TEAM         = 0x00;
static const uintptr_t O_CAMERA_MAT   = 0x00;
// ======================================================================

int main(int argc, char** argv) {
    if (argc < 2) return 1;
    const char* shmPath = argv[1];
    const char* pkg = "com.dts.freefireth"; // MAX: com.dts.freefiremax

    int fd = open(shmPath, O_RDWR);
    g_out = (EspData*)mmap(nullptr, sizeof(EspData),
                           PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);

    int sw = 1920, sh = 1080; // set to device resolution

    for (;;) {
        if (g_pid <= 0 || !libBase(g_pid, "libil2cpp.so")) {
            g_pid = findPid(pkg);
            usleep(300000);
            continue;
        }
        uintptr_t base = libBase(g_pid, "libil2cpp.so");

        uintptr_t mgr   = R<uintptr_t>(base + O_LIB_BASE_PTR);
        uintptr_t cam   = R<uintptr_t>(mgr + O_CAMERA_MAT);
        Mat4 vp         = R<Mat4>(cam);
        uintptr_t list  = R<uintptr_t>(mgr + O_PLAYER_LIST);
        int n           = R<int>(mgr + O_PLAYER_COUNT);
        uintptr_t local = R<uintptr_t>(mgr + O_LOCAL_PLAYER);
        int myTeam      = R<int>(local + O_TEAM);

        int out = 0; float best = 1e9f, bdx = 0, bdy = 0;
        for (int i = 0; i < n && out < 64; i++) {
            uintptr_t p = R<uintptr_t>(list + i * 8);
            if (!p || p == local) continue;
            int team = R<int>(p + O_TEAM);
            int hp   = R<int>(p + O_HEALTH);
            if (team == myTeam || hp <= 0) continue;

            float x = R<float>(p + O_POS);
            float y = R<float>(p + O_POS + 4);
            float z = R<float>(p + O_POS + 8);
            uintptr_t hb = R<uintptr_t>(p + O_HEAD_BONE);
            float hz = R<float>(hb + 4);

            float sx, sy, hx, hy;
            if (!w2s(vp, x, y, z, sw, sh, sx, sy)) continue;
            w2s(vp, x, hz, z, sw, sh, hx, hy);

            float dx = hx - sw * 0.5f, dy = hy - sh * 0.5f;
            float d2 = dx * dx + dy * dy;
            if (d2 < best) { best = d2; bdx = dx / (sw * 0.5f); bdy = dy / (sh * 0.5f); }

            g_out->e[out] = { sx, sy, hy, (float)z, hp, 1 };
            out++;
        }
        g_out->count = out;
        g_out->aimDx = bdx;
        g_out->aimDy = bdy;
        usleep(8000);
    }
}