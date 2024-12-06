package com.zxc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.*;

public class PairSumProcessor {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // User input for array size and bounds
        System.out.println("Enter the size of the array:");
        int size = scanner.nextInt();
        System.out.println("Enter the lower bound of the range:");
        int lowerBound = scanner.nextInt();
        System.out.println("Enter the upper bound of the range:");
        int upperBound = scanner.nextInt();

        // Generate random array
        List<Integer> numbers = generateRandomArray(size, lowerBound, upperBound);
        System.out.println("Generated array: " + numbers);

        // Measure time for Work Stealing
        ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
        long startTime = System.currentTimeMillis();
        int resultWorkStealing = forkJoinPool.invoke(new PairSumTask(numbers, 0, numbers.size()));
        long endTime = System.currentTimeMillis();
        System.out.println("Result (Work Stealing): " + resultWorkStealing);
        System.out.println("Execution time (Work Stealing): " + (endTime - startTime) + " ms");

        // Measure time for Work Dealing
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        startTime = System.currentTimeMillis();
        int resultWorkDealing = processWithWorkDealing(numbers, executorService);
        endTime = System.currentTimeMillis();
        executorService.shutdown();

        System.out.println("Result (Work Dealing): " + resultWorkDealing);
        System.out.println("Execution time (Work Dealing): " + (endTime - startTime) + " ms");
    }

    private static List<Integer> generateRandomArray(int size, int lowerBound, int upperBound) {
        Random random = new Random();
        List<Integer> array = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            array.add(random.nextInt(upperBound - lowerBound + 1) + lowerBound);
        }
        return array;
    }

    static class PairSumTask extends RecursiveTask<Integer> {
        private static final int THRESHOLD = 10;
        private final List<Integer> numbers;
        private final int start;
        private final int end;

        public PairSumTask(List<Integer> numbers, int start, int end) {
            this.numbers = numbers;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Integer compute() {
            if (end - start <= THRESHOLD) {
                int sum = 0;
                for (int i = start; i < end - 1; i++) {
                    sum += numbers.get(i) + numbers.get(i + 1);
                }
                return sum;
            } else {
                int mid = (start + end) / 2;
                PairSumTask leftTask = new PairSumTask(numbers, start, mid);
                PairSumTask rightTask = new PairSumTask(numbers, mid, end);

                leftTask.fork();
                int rightResult = rightTask.compute();
                int leftResult = leftTask.join();

                return leftResult + rightResult;
            }
        }
    }

    private static int processWithWorkDealing(List<Integer> numbers, ExecutorService executorService) {
        int chunkSize = numbers.size() / Runtime.getRuntime().availableProcessors();
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < numbers.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, numbers.size());
            List<Integer> chunk = numbers.subList(i, end);
            futures.add(executorService.submit(() -> {
                int sum = 0;
                for (int j = 0; j < chunk.size() - 1; j++) {
                    sum += chunk.get(j) + chunk.get(j + 1);
                }
                return sum;
            }));
        }

        int totalSum = 0;
        for (Future<Integer> future : futures) {
            try {
                totalSum += future.get();
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Error processing chunk: " + e.getMessage());
            }
        }

        return totalSum;
    }
}