package com.yesman.akythera.client.core.asset.loader;

import java.util.ArrayList;
import java.util.List;

/// Vertex indices for position, normal, uv in buffer
public record VertexBuilder(int position, int uv, int normal) {
    public static List<VertexBuilder> create(int[] drawIndices) {
        List<VertexBuilder> vertexIndicators = new ArrayList<>();

        for (int i = 0; i < drawIndices.length / 3; i++) {
            int k = i * 3;
            int position = drawIndices[k];
            int uv = drawIndices[k + 1];
            int normal = drawIndices[k + 2];
            VertexBuilder vi = new VertexBuilder(position, uv, normal);
            vertexIndicators.add(vi);
        }

        return vertexIndicators;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof VertexBuilder(int position1, int uv1, int normal1)) {
            return position == position1 && uv == uv1 && normal == normal1;
        }

        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + position;
        result = prime * result + uv;
        result = prime * result + normal;

        return result;
    }
}