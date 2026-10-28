package dev.tmdevelopment.flaremessages.face;

/** Steve face, used when the skin can't be downloaded. */
final class DefaultFaces {

    private DefaultFaces() {
    }

    private static final int H = 0x2B1E0D; // dark hair
    private static final int h = 0x3D2A14; // hair
    private static final int S = 0xB78272; // skin
    private static final int s = 0xA0705C; // skin shadow
    private static final int W = 0xFFFFFF; // eye white
    private static final int E = 0x523D89; // iris
    private static final int N = 0x8A5A47; // nose
    private static final int M = 0x6A4030; // mouth

    static final int[] STEVE = {
            H, H, H, H, H, H, H, H,
            H, h, H, H, H, H, h, H,
            H, S, S, S, S, S, S, H,
            S, S, S, S, S, S, S, S,
            S, W, E, S, S, E, W, S,
            s, S, S, N, N, S, S, s,
            s, S, M, S, S, M, S, s,
            s, s, M, M, M, M, s, s,
    };
}
