package dev.isoforge.reveal;

import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * Snapshot imutavel do reveal. Lido por threads de malha (vanilla e Embeddium) e pela render thread,
 * entao nunca e modificado depois de publicado.
 *
 * Dois cortes:
 *  1) Teto: tudo com y >= cutY dentro de um circulo de raio {@code radius} em volta do jogador.
 *  2) Tubo de visao: funil que vai do jogador ate a camera, escondendo o que fica na frente dele
 *     (somente acima do bloco ocupado pelos pes, preservando o piso e blocos de apoio).
 */
public record RevealState(
        boolean active,
        int px, int py, int pz,
        int cutY,
        int radius,
        Map<Long, Integer> roomCuts,
        double bx, double by, double bz,   // vetor unitario do jogador para a camera
        double rNear, double rFar, double ramp,
        double minAlong, double reach) {

    public static final int NO_CUT = Integer.MAX_VALUE;
    public static final RevealState OFF = new RevealState(false, 0, 0, 0, NO_CUT, 0, Map.of(),
            0, 1, 0, 0, 0, 1, 0, 0);

    public double ax() { return px + 0.5; }
    public double ay() { return py + 1.0; }
    public double az() { return pz + 0.5; }

    public boolean hasCut() {
        return cutY != NO_CUT;
    }

    /** Escondido pelo corte de teto (vale tambem para entidades). */
    public boolean hiddenByCeiling(int x, int y, int z) {
        if (!active || y < cutY) return false;
        long dx = x - px;
        long dz = z - pz;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    /**
     * Entidades tambem respeitam os tetos detectados por coluna.
     * Antes, somente o corte circular global era aplicado, fazendo mobs/NPCs
     * acima do teto de um comodo continuarem visiveis atraves do reveal.
     */
    public boolean hiddenEntity(int x, int y, int z) {
        return active && (hiddenByRoomRoof(x, y, z) || hiddenByCeiling(x, y, z));
    }

    public boolean hiddenByRoomRoof(int x, int y, int z) {
        if (!active) return false;
        Integer cut = roomCuts.get(BlockPos.asLong(x, 0, z));
        return cut != null && y >= cut;
    }

    public boolean hiddenByTube(int x, int y, int z) {
        if (!active || y <= py) return false;
        double rx = x + 0.5 - ax();
        double ry = y + 0.5 - ay();
        double rz = z + 0.5 - az();
        double s = rx * bx + ry * by + rz * bz;
        if (s < minAlong || s > reach) return false;
        double lat2 = rx * rx + ry * ry + rz * rz - s * s;
        double t = Math.min(1.0, s / ramp);
        double r = rNear + (rFar - rNear) * t;
        return lat2 <= r * r;
    }

    public boolean isHidden(int x, int y, int z) {
        return active && (hiddenByRoomRoof(x, y, z) || hiddenByCeiling(x, y, z) || hiddenByTube(x, y, z));
    }

    /**
     * Teste rapido e conservador: a secao 16x16x16 em (sx,sy,sz) pode conter blocos escondidos por este estado?
     */
    public boolean mayAffectSection(int sx, int sy, int sz) {
        if (!active) return false;
        int x0 = sx << 4, y0 = sy << 4, z0 = sz << 4;
        // teto
        if (hasCut() && y0 + 15 >= cutY) {
            int nx = Math.max(x0, Math.min(px, x0 + 15));
            int nz = Math.max(z0, Math.min(pz, z0 + 15));
            long dx = nx - px, dz = nz - pz;
            if (dx * dx + dz * dz <= (long) radius * radius) return true;
        }
        for (Map.Entry<Long, Integer> entry : roomCuts.entrySet()) {
            int x = BlockPos.getX(entry.getKey());
            int z = BlockPos.getZ(entry.getKey());
            if (x >= x0 && x <= x0 + 15 && z >= z0 && z <= z0 + 15
                    && y0 + 15 >= entry.getValue()) return true;
        }
        // tubo (meia diagonal de uma secao ~ 13.86)
        double cx = x0 + 8 - ax(), cy = y0 + 8 - ay(), cz = z0 + 8 - az();
        double s = cx * bx + cy * by + cz * bz;
        if (s >= minAlong - 14.0 && s <= reach + 14.0 && y0 + 15 > py) {
            double lat2 = cx * cx + cy * cy + cz * cz - s * s;
            double lim = rFar + 14.0;
            if (lat2 <= lim * lim) return true;
        }
        return false;
    }
}
