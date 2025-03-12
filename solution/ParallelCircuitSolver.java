package cp2024.solution;

import cp2024.circuit.CircuitNode;
import cp2024.circuit.CircuitSolver;
import cp2024.circuit.CircuitValue;
import cp2024.circuit.Circuit;
import cp2024.demo.BrokenCircuitValue;

import java.util.concurrent.*;

//class for a circuit solver that enables parallel computations.
//returns circuitValue and starts computations after solve() method call.
//evaluation process is described further in the ParallelCircuitValue class.
public class ParallelCircuitSolver implements CircuitSolver{
    private final ExecutorService executorsPool;
    private boolean wasStopped;
    public ParallelCircuitSolver(){
        this.wasStopped=false;
        executorsPool = Executors.newCachedThreadPool();
    }
    @Override
    public CircuitValue solve(Circuit c) {
        if(wasStopped){
            return new BrokenCircuitValue();
        }
        //creates an ExecutorService instance
        CircuitNode root = c.getRoot();
        BlockingQueue<Boolean> result = new LinkedBlockingQueue<>();
        //creates new ParallelCircuitValue object, starts computations and returns the circuit value
        ParallelCircuitValue circuitValue = new ParallelCircuitValue(root, executorsPool, result);
        executorsPool.submit(circuitValue);
        return circuitValue;
    }

    //stops computations for the current Circuit, disables solve() method calls
    @Override
    public void stop() {
        wasStopped=true;
        executorsPool.shutdownNow();
    }

}
