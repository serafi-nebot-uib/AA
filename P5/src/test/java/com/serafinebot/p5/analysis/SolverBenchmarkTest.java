package com.serafinebot.p5.analysis;

import com.serafinebot.p5.model.GameInput;
import com.serafinebot.p5.model.SolverMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolverBenchmarkTest {

    @Test
    void measuresBothDynamicProgrammingStrategies() {
        GameInput input = new GameInput(2, 2, 0, 5, 0, 2, 1);
        List<SolverMeasurement> measurements = SolverBenchmark.measureAll(input, 0, 1);

        assertEquals(2, measurements.size());
        assertEquals(SolverMode.TOP_DOWN_DP, measurements.get(0).solverMode());
        assertEquals(SolverMode.BOTTOM_UP_DP, measurements.get(1).solverMode());
        assertEquals(measurements.get(0).losingPlayer(), measurements.get(1).losingPlayer());
        assertTrue(measurements.get(0).wallNanos() >= 0);
        assertTrue(measurements.get(1).wallNanos() >= 0);
    }

    @Test
    void defaultInputsProvideGrowthCasesForTheReport() {
        List<GameInput> inputs = SolverBenchmark.defaultInputs();

        assertFalse(inputs.isEmpty());
        assertTrue(inputs.stream().anyMatch(input -> input.width() == 2 && input.height() == 2));
        assertTrue(inputs.stream().anyMatch(input -> input.width() == 5 && input.height() == 5));
        assertTrue(inputs.stream().anyMatch(input -> input.limit() == 100));
    }
}
