package dev.isoforge.input;

/** Travessia de voxels (Amanatides-Woo) ao longo de um segmento. Sem dependencias do Minecraft. */
public final class Dda {
    public interface Visitor {
        /** @return true para parar a travessia. */
        boolean visit(int x, int y, int z);
    }

    private Dda() {}

    public static void traverse(double sx, double sy, double sz, double ex, double ey, double ez,
                                int maxSteps, Visitor v) {
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        int x = (int) Math.floor(sx), y = (int) Math.floor(sy), z = (int) Math.floor(sz);
        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);
        double inf = Double.POSITIVE_INFINITY;
        double tMaxX = stepX == 0 ? inf : ((stepX > 0 ? x + 1 : x) - sx) / dx;
        double tMaxY = stepY == 0 ? inf : ((stepY > 0 ? y + 1 : y) - sy) / dy;
        double tMaxZ = stepZ == 0 ? inf : ((stepZ > 0 ? z + 1 : z) - sz) / dz;
        double tDeltaX = stepX == 0 ? inf : stepX / dx;
        double tDeltaY = stepY == 0 ? inf : stepY / dy;
        double tDeltaZ = stepZ == 0 ? inf : stepZ / dz;

        for (int i = 0; i < maxSteps; i++) {
            if (v.visit(x, y, z)) return;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                if (tMaxX > 1.0) return;
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                if (tMaxY > 1.0) return;
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > 1.0) return;
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
    }
}
