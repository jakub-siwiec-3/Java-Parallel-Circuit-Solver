# Parallel Circuit Solver

## Overview

This program evaluates logical expressions in parallel, leveraging parallel computing mechanisms. It employs a **greedy evaluation strategy**, terminating computation as soon as the final result is determined.

## Features

- **Parallel evaluation of logical circuits** using multithreading.
- **Greedy computation strategy**, stopping unnecessary calculations once the result is known.
- **Support for various logic gate types** (AND, OR, NOT, IF, GT, LT, LEAF).
- **Efficient thread management** using Java's concurrency utilities.

## Implementation Details

### `ParallelCircuitSolver`
This class is responsible for managing the parallel execution of logical circuit evaluation.

- Uses an **`ExecutorService`** (`CachedThreadPool`) to manage worker threads dynamically.
- Calls the `solve()` method to create a **`ParallelCircuitValue`** object and starts its computation.
- Supports stopping computations using the `stop()` method, which shuts down the executor.

### `ParallelCircuitValue`
This class handles the recursive, parallel evaluation of logical circuits.

- Implements **`Runnable`**, allowing execution as a separate thread.
- Each node's value is computed asynchronously and passed to its parent through **blocking queues** (`LinkedBlockingQueue`).
- Uses **future tasks (`Future<?>`)** to track and manage active computations.
- Supports **early termination** by canceling unnecessary computations once a decisive result is reached.
- Different logic gates (AND, OR, NOT, GT, LT, IF, LEAF) are evaluated using their respective methods.

