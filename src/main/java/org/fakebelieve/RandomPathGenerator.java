package org.fakebelieve;

import java.util.Random;
import java.util.StringJoiner;

public class RandomPathGenerator {
    private final Random random;

    public RandomPathGenerator() {
        this.random = new Random();
    }

    public RandomPathGenerator(Random random) {
        this.random = random;
    }

    public String generatePath() {
        StringJoiner pathJoiner = new StringJoiner("/");

        // First 3 segments with 4 digits each
        for (int i = 0; i < 3; i++) {
            pathJoiner.add(generateSegment(4));
        }

        // Last segment with 8 digits
        pathJoiner.add(generateSegment(8));

        return pathJoiner.toString();
    }

    private String generateSegment(int length) {
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < length; i++) {
            segment.append(random.nextBoolean() ? '1' : '0');
        }
        return segment.toString();
    }

    public static void main(String[] args) {
        RandomPathGenerator generator = new RandomPathGenerator();

        // Generate some example paths
        for (int i = 0; i < 5; i++) {
            System.out.println(generator.generatePath());
        }
    }
}
