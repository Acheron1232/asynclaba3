package com.zxc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FileCounter {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // User input for directory path and file extension
        System.out.println("Enter the directory path:");
        String directoryPath = scanner.nextLine();
        System.out.println("Enter the file extension (e.g., .pdf):");
        String fileExtension = scanner.nextLine();

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            System.out.println("Invalid directory path.");
            return;
        }

        // Measure time for Work Stealing
        ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
        long startTime = System.currentTimeMillis();
        int countWorkStealing = forkJoinPool.invoke(new FileCountTask(directory, fileExtension));
        long endTime = System.currentTimeMillis();

        System.out.println("File count (Work Stealing): " + countWorkStealing);
        System.out.println("Execution time (Work Stealing): " + (endTime - startTime) + " ms");

        // Measure time for Work Dealing
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        startTime = System.currentTimeMillis();
        int countWorkDealing = processWithWorkDealing(directory, fileExtension, executorService);
        endTime = System.currentTimeMillis();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                System.err.println("Executor service did not terminate in the specified time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Executor service termination interrupted.");
        }

        System.out.println("File count (Work Dealing): " + countWorkDealing);
        System.out.println("Execution time (Work Dealing): " + (endTime - startTime) + " ms");
    }

    static class FileCountTask extends RecursiveTask<Integer> {
        private final File directory;
        private final String fileExtension;

        public FileCountTask(File directory, String fileExtension) {
            this.directory = directory;
            this.fileExtension = fileExtension;
        }

        @Override
        protected Integer compute() {
            File[] files = directory.listFiles();
            if (files == null || files.length == 0) {
                return 0;
            }

            int count = 0;
            List<FileCountTask> subTasks = new ArrayList<>();

            for (File file : files) {
                if (file.isDirectory()) {
                    FileCountTask subTask = new FileCountTask(file, fileExtension);
                    subTasks.add(subTask);
                    subTask.fork();
                } else if (file.getName().endsWith(fileExtension)) {
                    count++;
                }
            }

            for (FileCountTask subTask : subTasks) {
                count += subTask.join();
            }

            return count;
        }
    }

    private static int processWithWorkDealing(File directory, String fileExtension, ExecutorService executorService) {
        AtomicInteger totalCount = new AtomicInteger();
        List<File> directoriesToProcess = new ArrayList<>();
        directoriesToProcess.add(directory);

        while (!directoriesToProcess.isEmpty()) {
            List<Future<List<File>>> futures = new ArrayList<>();
            List<File> nextBatch = new ArrayList<>();

            for (File currentDirectory : directoriesToProcess) {
                File[] files = currentDirectory.listFiles();
                if (files == null || files.length == 0) {
                    continue;
                }

                for (File file : files) {
                    if (file.isDirectory()) {
                        nextBatch.add(file);
                    } else if (file.getName().endsWith(fileExtension)) {
                        totalCount.getAndIncrement();
                    }
                }
            }

            for (File subDirectory : nextBatch) {
                futures.add(executorService.submit(() -> {
                    List<File> subDirs = new ArrayList<>();
                    File[] files = subDirectory.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isDirectory()) {
                                subDirs.add(file);
                            } else if (file.getName().endsWith(fileExtension)) {
                                totalCount.getAndIncrement();
                            }
                        }
                    }
                    return subDirs;
                }));
            }

            directoriesToProcess.clear();
            for (Future<List<File>> future : futures) {
                try {
                    directoriesToProcess.addAll(future.get(5, TimeUnit.SECONDS));
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    System.err.println("Error processing directory: " + e.getMessage());
                }
            }
        }

        return totalCount.get();
    }
}
