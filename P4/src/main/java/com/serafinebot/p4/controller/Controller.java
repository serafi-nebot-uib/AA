package com.serafinebot.p4.controller;

import com.serafinebot.p4.model.codec.HuffmanCodec;
import com.serafinebot.p4.model.progress.ProgressSnapshot;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.report.CompressionReport;
import com.serafinebot.p4.model.report.DecompressionReport;
import com.serafinebot.p4.view.MainWindow;
import com.serafinebot.p4.view.ViewListener;

import javax.swing.SwingWorker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * MVC controller that connects the Swing view to the Huffman model.
 */
public class Controller implements ViewListener {

    private final MainWindow view;
    private SwingWorker<?, ProgressSnapshot> worker;

    public Controller(MainWindow view) {
        this.view = view;
        this.view.setViewListener(this);
    }

    @Override
    public void onCompressRequested(Path inputPath, Path outputPath, PriorityQueueStrategy strategy) {
        if (!validateInputPath(inputPath) || !validateOutputPath(outputPath)) {
            return;
        }

        view.clearResults();
        view.showStatus("Compressio en curs...");
        view.setRunning(true);

        worker = new SwingWorker<>() {
            @Override
            protected CompressionReport doInBackground() throws Exception {
                return new HuffmanCodec(strategy).compressWithReport(inputPath, outputPath, snapshot -> publish(snapshot));
            }

            @Override
            protected void process(List<ProgressSnapshot> chunks) {
                view.updateProgress(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                view.setRunning(false);
                handleCompressionResult(inputPath, outputPath);
            }
        };
        worker.execute();
    }

    @Override
    public void onDecompressRequested(Path inputPath, Path outputPath, PriorityQueueStrategy strategy) {
        if (!validateInputPath(inputPath) || !validateOutputPath(outputPath)) {
            return;
        }

        view.clearResults();
        view.showStatus("Descompressio en curs...");
        view.setRunning(true);

        worker = new SwingWorker<>() {
            @Override
            protected DecompressionReport doInBackground() throws Exception {
                return new HuffmanCodec(strategy).decompressWithReport(inputPath, outputPath, snapshot -> publish(snapshot));
            }

            @Override
            protected void process(List<ProgressSnapshot> chunks) {
                view.updateProgress(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                view.setRunning(false);
                handleDecompressionResult(inputPath, outputPath);
            }
        };
        worker.execute();
    }

    private void handleCompressionResult(Path inputPath, Path outputPath) {
        try {
            CompressionReport report = (CompressionReport) worker.get();
            view.showCompressionReport(report);
            view.refreshFileExplorer();
            view.showStatus("S'ha comprimit " + inputPath.getFileName() + " a " + outputPath.getFileName() + ".");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            view.showError("La compressio s'ha interromput.");
        } catch (ExecutionException exception) {
            view.showError(rootMessage(exception));
        }
    }

    private void handleDecompressionResult(Path inputPath, Path outputPath) {
        try {
            DecompressionReport report = (DecompressionReport) worker.get();
            view.showDecompressionReport(report);
            view.refreshFileExplorer();
            view.showStatus("S'ha descomprimit " + inputPath.getFileName() + " a " + outputPath.getFileName() + ".");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            view.showError("La descompressio s'ha interromput.");
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
