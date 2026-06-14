package com.aicode.agent.index;

import java.util.Map;

public final class VectorMath {
    private VectorMath() {}

    public static double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (Map.Entry<String, Double> entry : a.entrySet()) {
            double va = entry.getValue();
            normA += va * va;
            Double vb = b.get(entry.getKey());
            if (vb != null) {
                dot += va * vb;
            }
        }
        for (double vb : b.values()) {
            normB += vb * vb;
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
