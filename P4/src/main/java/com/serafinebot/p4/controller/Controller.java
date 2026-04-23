package com.serafinebot.p4.controller;

import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.benchmark.BenchmarkConfig;
import com.serafinebot.p4.model.benchmark.BenchmarkProgressSnapshot;
import com.serafinebot.p4.model.benchmark.BenchmarkReport;
import com.serafinebot.p4.model.benchmark.BenchmarkService;
import com.serafinebot.p4.model.codec.HuffmanCodec;
import com.serafinebot.p4.model.progress.ProgressSnapshot;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.info.CompressionInfo;
import com.serafinebot.p4.model.info.DecompressionInfo;
import com.serafinebot.p4.view.MainWindow;
import com.serafinebot.p4.view.ViewListener;

import javax.swing.SwingWorker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * MVC controller that connects the Swing view to the Huffman model.
 */
public class Controller implements ViewListener {

    private final MainWindow view;

    public Controller(MainWindow view) {
        this.view = view;
        this.view.setViewListener(this);
    }

    @Override
    public void onCompressRequested(Path inputPath,
                                    Path outputPath,
                                    PriorityQueueStrategy strategy,
                                    CompressionMode requestedMode) {
        if (!validateInputPath(inputPath) || !validateOutputPath(outputPath)) {
            return;
        }

        view.clearResults();
        view.showStatus("Compressio en curs...");
        view.setRunning(true);

        SwingWorker<CompressionInfo, ProgressSnapshot> compressionWorker = new SwingWorker<>() {
            @Override
            protected CompressionInfo doInBackground() throws Exception {
                return new HuffmanCodec(strategy, requestedMode)
                    .compressWithInfo(inputPath, outputPath, snapshot -> publish(snapshot));
            }

            @Override
            protected void process(List<ProgressSnapshot> chunks) {
                view.updateProgress(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                view.setRunning(false);
                handleCompressionResult(this, inputPath, outputPath);
            }
        };
        compressionWorker.execute();
    }

    @Override
    public void onDecompressRequested(Path inputPath, Path outputPath, PriorityQueueStrategy strategy) {
        if (!validateInputPath(inputPath) || !validateOutputPath(outputPath)) {
            return;
        }

        view.clearResults();
        view.showStatus("Descompressio en curs...");
        view.setRunning(true);

        SwingWorker<DecompressionInfo, ProgressSnapshot> decompressionWorker = new SwingWorker<>() {
            @Override
            protected DecompressionInfo doInBackground() throws Exception {
                return new HuffmanCodec(strategy).decompressWithInfo(inputPath, outputPath, snapshot -> publish(snapshot));
            }

            @Override
            protected void process(List<ProgressSnapshot> chunks) {
                view.updateProgress(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                view.setRunning(false);
                handleDecompressionResult(this, inputPath, outputPath);
            }
        };
        decompressionWorker.execute();
    }

    @Override
    public void onBenchmarkRequested(BenchmarkConfig config) {
        view.setRunning(true);
        view.resetBenchmarkProgress();

        SwingWorker<BenchmarkReport, BenchmarkProgressSnapshot> benchmarkWorker = new SwingWorker<>() {
            @Override
            protected BenchmarkReport doInBackground() throws Exception {
                return new BenchmarkService().run(config, this::publish);
            }

            @Override
            protected void process(List<BenchmarkProgressSnapshot> chunks) {
                view.updateBenchmarkProgress(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                view.setRunning(false);
                handleBenchmarkResult(this);
            }
        };
        benchmarkWorker.execute();
    }

    private void handleCompressionResult(SwingWorker<CompressionInfo, ProgressSnapshot> worker,
                                         Path inputPath,
                                         Path outputPath) {
        try {
            CompressionInfo info = worker.get();
            if (rejectOversizedArchive(info, outputPath)) {
                return;
            }
            view.showCompressionInfo(info);
            view.refreshFileExplorer();
            view.showStatus("S'ha comprimit " + inputPath.getFileName() + " a " + outputPath.getFileName() + ".");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            view.showError("La compressio s'ha interromput.");
        } catch (ExecutionException exception) {
            view.showError(rootMessage(exception));
        }
    }

    private boolean rejectOversizedArchive(CompressionInfo info, Path outputPath) {
        if (info.stats().archiveSize() <= info.stats().originalSize()) {
            return false;
        }

        try {
            Files.deleteIfExists(outputPath);
            view.refreshFileExplorer();
            view.showError("La compressio generaria un arxiu mes gran que l'original; s'ha eliminat el fitxer de sortida.");
        } catch (IOException exception) {
            view.showError("La compressio generaria un arxiu mes gran que l'original, pero no s'ha pogut eliminar el fitxer de sortida: " + exception.getMessage());
        }
        return true;
    }

    private void handleDecompressionResult(SwingWorker<DecompressionInfo, ProgressSnapshot> worker,
                                           Path inputPath,
                                           Path outputPath) {
        try {
            DecompressionInfo info = worker.get();
            view.showDecompressionInfo(info);
            view.refreshFileExplorer();
            view.showStatus("S'ha descomprimit " + inputPath.getFileName() + " a " + outputPath.getFileName() + ".");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            view.showError("La descompressio s'ha interromput.");
        } catch (ExecutionException exception) {
            view.showError(rootMessage(exception));
        }
    }

    private void handleBenchmarkResult(SwingWorker<BenchmarkReport, BenchmarkProgressSnapshot> worker) {
        try {
            BenchmarkReport report = worker.get();
            view.showBenchmarkReport(report);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            view.showError("L'execucio de les comparatives s'ha interromput.");
        } catch (ExecutionException exception) {
            view.showError(rootMessage(exception));
        }
    }

    private boolean validateInputPath(Path inputPath) {
        if (!Files.isRegularFile(inputPath)) {
            view.showError("La ruta d'entrada ha d'apuntar a un fitxer existent.");
            return false;
        }
        return true;
    }

    private boolean validateOutputPath(Path outputPath) {
        if (Files.isDirectory(outputPath)) {
            view.showError("La ruta de sortida ha de ser un fitxer, no un directori.");
            return false;
        }
        Path parent = outputPath.toAbsolutePath().normalize().getParent();
        if (parent != null && !Files.exists(parent)) {
            view.showError("El directori de sortida no existeix.");
            return false;
        }
        return true;
    }

    private String rootMessage(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause != null && cause.getMessage() != null ? cause.getMessage() : exception.getMessage();
    }
}
