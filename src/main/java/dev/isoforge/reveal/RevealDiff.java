package dev.isoforge.reveal;

/**
 * Calcula quais secoes de chunk mudam de malha quando o reveal passa do estado A para o estado B.
 * Inclui vizinhas quando o bloco alterado fica na borda da secao (a face do vizinho muda de visivel/oculta).
 */
public final class RevealDiff {
    public interface SectionSink {
        void accept(int sx, int sy, int sz);
    }

    private RevealDiff() {}

    public static void compute(RevealState a, RevealState b, int minSec, int maxSecExclusive, SectionSink sink) {
        if (a.equals(b)) return;
        if (!a.active() && !b.active()) return;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (RevealState s : new RevealState[]{a, b}) {
            if (!s.active()) continue;
            double ext = Math.max(s.reach() + s.rFar() + 16.0, s.radius() + 16.0);
            minX = Math.min(minX, (int) Math.floor(s.px() - ext));
            maxX = Math.max(maxX, (int) Math.ceil(s.px() + ext));
            minZ = Math.min(minZ, (int) Math.floor(s.pz() - ext));
            maxZ = Math.max(maxZ, (int) Math.ceil(s.pz() + ext));
        }
        int sx0 = minX >> 4, sx1 = maxX >> 4, sz0 = minZ >> 4, sz1 = maxZ >> 4;

        for (int sx = sx0; sx <= sx1; sx++) {
            for (int sz = sz0; sz <= sz1; sz++) {
                for (int sy = minSec; sy < maxSecExclusive; sy++) {
                    if (!a.mayAffectSection(sx, sy, sz) && !b.mayAffectSection(sx, sy, sz)) continue;
                    diffSection(a, b, sx, sy, sz, sink);
                }
            }
        }
    }

    private static void diffSection(RevealState a, RevealState b, int sx, int sy, int sz, SectionSink sink) {
        int x0 = sx << 4, y0 = sy << 4, z0 = sz << 4;
        boolean self = false;
        boolean nxm = false, nxp = false, nym = false, nyp = false, nzm = false, nzp = false;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int ly = 0; ly < 16; ly++) {
                    int x = x0 + lx, y = y0 + ly, z = z0 + lz;
                    if (a.isHidden(x, y, z) == b.isHidden(x, y, z)) continue;
                    self = true;
                    if (lx == 0) nxm = true;
                    if (lx == 15) nxp = true;
                    if (ly == 0) nym = true;
                    if (ly == 15) nyp = true;
                    if (lz == 0) nzm = true;
                    if (lz == 15) nzp = true;
                }
            }
        }
        if (!self) return;
        sink.accept(sx, sy, sz);
        if (nxm) sink.accept(sx - 1, sy, sz);
        if (nxp) sink.accept(sx + 1, sy, sz);
        if (nym) sink.accept(sx, sy - 1, sz);
        if (nyp) sink.accept(sx, sy + 1, sz);
        if (nzm) sink.accept(sx, sy, sz - 1);
        if (nzp) sink.accept(sx, sy, sz + 1);
    }
}
