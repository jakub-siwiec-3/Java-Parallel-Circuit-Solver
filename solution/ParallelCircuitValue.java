package cp2024.solution;

import cp2024.circuit.*;

import java.util.ArrayList;
import java.util.concurrent.*;

//class for evaluating a circuit value;
//the result of each subtree is submitted to the parentQueue;
//each node (except leaf nodes) is waiting on a blocking queue for the results from children;
//after receiving a value from a child, the node value is evaluated;
//when specific condition for a particular nodeType is met, Thread submits the value to the parentQueue and cancel remaining
//computations performed by its children
public class ParallelCircuitValue implements CircuitValue, Runnable {
    private final CircuitNode root;
    //all of ParallelCircuitValue objects operate on one executorsPool unique to each solver;
    //this helps with shutting down unnecessary computations (after the circuit is already evaluated)
    private final ExecutorService executorsPool;
    //pointer to the parents blocking queue
    private final BlockingQueue<Boolean> parentQueue;
    //blocking queue for children to submit their values
    private BlockingQueue<Boolean> childrenQueue;
    //arraylist for submitting tasks
    private final ArrayList<Future<?>> childrenFutures;

    public ParallelCircuitValue(CircuitNode root, ExecutorService executorsPool, BlockingQueue<Boolean> parentQueue) {
        this.root = root;
        this.executorsPool = executorsPool;
        this.parentQueue = parentQueue;
        this.childrenFutures = new ArrayList<>();
    }

    //returns args of the node
    private CircuitNode[] getChildren() throws InterruptedException {
        CircuitNode[] children;

        children = this.root.getArgs();
        return children;
    }

    //submits new ParallelCircuitValue to executorsPool
    private void submitChild(CircuitNode child) {
        childrenFutures.add
                (executorsPool.submit(new ParallelCircuitValue(child, executorsPool, childrenQueue)));
    }

    //stops unnecessary computations when thread is interrupted or the value is already evaluated
    private void stopChildren() {
        for (Future future : childrenFutures) {
            future.cancel(true);
        }
    }

    //submits all children (args[]) to executorsPool
    private void submitChildren(CircuitNode[] args) {
        for (CircuitNode arg : args) {
            submitChild(arg);
        }
    }

    //evaluates the subtree value for AND type according to the specification
    private void solveAND() throws InterruptedException {
        CircuitNode[] args = getChildren();
        int nChildren = args.length;

        boolean allTrue = true;

        submitChildren(args);

        //waiting for children values and evaluating the value of the subtree
        for (int i = 0; i < nChildren; i++) {
            //checking for a potential interruption and stopping computations if needed
            if (Thread.currentThread().isInterrupted()) {
                stopChildren();
                return;
            }
            Boolean value = childrenQueue.take();
            if (!value) {
                allTrue = false;
                break;
            }
        }

        //submitting the result for the parent's blocking queue
        Boolean nodeValue;
        if (!allTrue) {
            nodeValue = false;
            parentQueue.put(nodeValue);
            stopChildren();
        } else {
            nodeValue = true;
            parentQueue.put(nodeValue);
        }
    }

    //evaluates subtree value for OR type according to the specification
    private void solveOR() throws InterruptedException {
        CircuitNode[] args = getChildren();
        int nChildren = args.length;

        boolean allFalse = true;

        submitChildren(args);

        //waiting for children values and evaluating value of the subtree
        for (int i = 0; i < nChildren; i++) {
            if (Thread.currentThread().isInterrupted()) {
                stopChildren();
                return;
            }
            Boolean value = childrenQueue.take();
            if (value) {
                allFalse = false;
                break;
            }

        }
        //submitting the result for the parent's blocking queue
        Boolean nodeValue;
        if (allFalse) {
            nodeValue = false;
            parentQueue.put(nodeValue);
        } else {
            nodeValue = true;
            parentQueue.put(nodeValue);
            stopChildren();
        }
    }

    //evaluates the subtree value for IF type according to specification
    private void solveIF() throws InterruptedException, ExecutionException {
        CircuitNode[] args = getChildren();

        //we use 3 blocking queues to provide parallel evaluation
        BlockingQueue<Boolean> first = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> second = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> third = new LinkedBlockingQueue<>();

        Future<?> firstFuture = executorsPool.submit(new ParallelCircuitValue(args[0], executorsPool, first));
        Future<?> secondFuture = executorsPool.submit(new ParallelCircuitValue(args[1], executorsPool, second));
        Future<?> thirdFuture = executorsPool.submit(new ParallelCircuitValue(args[2], executorsPool, third));

        firstFuture.get();
        Boolean firstValue = first.take();

        Boolean nodeValue;
        if (firstValue) {
            //canceling the third branch if first==true
            thirdFuture.cancel(true);
            //submitting the result for the parent's blocking queue
            secondFuture.get();
            nodeValue = second.take();
            parentQueue.put(nodeValue);
        } else {
            //same thing
            secondFuture.cancel(true);
            thirdFuture.get();
            nodeValue = third.take();
            parentQueue.put(nodeValue);
        }
    }

    //evaluates LEAF value
    private void solveLEAF() throws InterruptedException {
        Boolean leafValue = ((LeafNode) root).getValue();
        parentQueue.put(leafValue);

    }

    //evaluates the subtree value for NOT type according to the specification
    private void solveNOT() throws InterruptedException {
        CircuitNode[] args = getChildren();

        submitChild(args[0]);
        Boolean nodeValue;

        Boolean value = childrenQueue.take();
        if (value) {
            nodeValue = false;
            parentQueue.put(nodeValue);
        } else {
            nodeValue = true;
            parentQueue.put(nodeValue);
        }

    }

    //evaluates the subtree value for GT type according to the specification
    private void solveGT() throws InterruptedException {
        CircuitNode[] args = getChildren();
        int nChildren = args.length;

        int threshold = ((ThresholdNode) root).getThreshold();
        Boolean nodeValue;
        if (threshold > nChildren) {
            nodeValue = false;
            parentQueue.put(nodeValue);
            return;
        }

        int nTrue = 0;
        int nFalse = 0;

        submitChildren(args);

        for (int i = 0; i < nChildren; i++) {
            if (Thread.currentThread().isInterrupted()) {
                stopChildren();
                return;
            }
            Boolean value = childrenQueue.take();
            if (value) {
                nTrue++;
                if (nTrue > threshold) {
                    break;
                }
            } else {
                nFalse++;
                if (nFalse >= nChildren - threshold) {
                    break;
                }
            }

        }
        if (nTrue + nFalse < nChildren) {
            stopChildren();
        }
        nodeValue = nTrue>threshold;
        parentQueue.put(nodeValue);
    }

    //evaluates the subtree value for LT type according to the specification
    private void solveLT() throws InterruptedException {
        CircuitNode[] args = getChildren();
        int nChildren = args.length;

        int nTrue = 0;
        int nFalse = 0;

        int threshold = ((ThresholdNode) root).getThreshold();
        Boolean nodeValue;
        submitChildren(args);
        for (int i = 0; i < nChildren; i++) {
            if (Thread.currentThread().isInterrupted()) {
                stopChildren();
                return;
            }
            Boolean value = childrenQueue.take();
            if (value) {
                nTrue++;
                if (nTrue >= threshold) {
                    //break if value is already known
                    break;
                }
            } else {
                nFalse++;
                if (nFalse > nChildren - threshold) {
                    break;
                }
            }
        }
        if (nTrue + nFalse < nChildren) {
            stopChildren();
        }
        nodeValue = nTrue<threshold;
        parentQueue.put(nodeValue);
    }

    @Override
    public boolean getValue() throws InterruptedException {
        //we have to wait on take() for the computations to end
        Boolean ret = parentQueue.take();
        //but we return it to the queue in case getValue() is called once again
        parentQueue.put(ret);
        return ret;
    }

    @Override
    public void run() {
        //evaluates subtree value, submits the result to parents' blocking queue
        childrenQueue = new LinkedBlockingQueue<>();
        NodeType type = root.getType();
        try {
            switch (type) {
                case AND -> this.solveAND();
                case NOT -> this.solveNOT();
                case LEAF -> this.solveLEAF();
                case GT -> this.solveGT();
                case LT -> this.solveLT();
                case OR -> this.solveOR();
                case IF -> this.solveIF();
            }
        } catch (InterruptedException | ExecutionException e) {
            //we stop computations if exception occurred
            stopChildren();
            return;
        }
        if (Thread.currentThread().isInterrupted()) {
            stopChildren();
        }
    }
}
