# Assignment 3: Particle Filter Implementation for Forklift Localization

This project implements a **Particle Filter** for forklift localization using LiDAR data and landmark references. The goal is to develop a filter that precisely estimates the vehicle's position during the simulation and analyzes the effect of various parameters on its performance.

## Project Structure
The implementation includes:
- **`src/particle_filter/src/particle_filter.cpp`**: Core implementation of the Particle Filter with prediction, weight update, and resampling functions.
- **`src/particle_filter/src/main.cpp`**: Main driver script that runs the filter with LiDAR input data.
- **`assignment3.pdf`**: Detailed technical report containing experimental analysis, parameter tuning results, and performance evaluation.

## Building and Running

### Prerequisites
- **OS**: Ubuntu 18.04/20.04
- **Framework**: ROS (Robot Operating System) - Melodic/Noetic
- **Tools**: `rosbag` for data playback

### Compilation
1. Navigate to the project root directory.
2. Source ROS setup:
   ```bash
   source /opt/ros/<distro>/setup.bash
   ```
3. Compile the project:
   ```bash
   catkin_make
   ```

### Execution
1. Start ROS Core in a separate terminal:
   ```bash
   roscore
   ```
2. Launch the particle node:
   ```bash
   ./devel/lib/particle/particle_node
   ```
3. In another terminal, play the rosbag:
   ```bash
   rosbag play --clock --hz=10 out.bag
   ```

## Experiments and Results
Multiple experiments were conducted by varying the number of particles and noise parameters. These experiments analyzed precision vs. stability tradeoffs. Detailed charts, plots, and analytical results are available in the **`assignment3.pdf`** report.
