# Platforms and Algorithms for Autonomous Driving - Planning and Control Module

This directory contains the assignments completed for the **Platforms and Algorithms for Autonomous Driving** course, focusing on the **Planning and Control Module**. Each assignment is organized into dedicated folders containing source code, simulation results, and technical reports.

---

## Assignment 1: Vehicle Modeling and Simulation

### Description
Focuses on the mathematical modeling and simulation of vehicle dynamics. Three models were implemented and compared:
- **Kinematic Model**
- **Linear Model**
- **Non-Linear Model**

Scenarios include sinusoidal steering and constant steering inputs. A comparative analysis between numerical integration methods (**Euler** vs. **RK4**) was also conducted across different time steps.

📄 Report: [Report_Assignment1.pdf](./assignment_1/Report_Assignment1.pdf)

---

## Assignment 2: Motion Control

### Description
Deep dive into longitudinal and lateral vehicle control across various speed conditions.
- **Exercise 1**: Longitudinal control using a **PID controller** for target speed tracking.
- **Exercise 2**: Low-speed lateral control (10 m/s and 20 m/s) using **Pure Pursuit** and **Stanley** controllers.
- **Exercise 3**: High-speed lateral control (23 m/s and 25 m/s) utilizing **curvature-based lookahead** strategies.

### Results
Analysis of lateral errors, steering command oscillations, and performance comparison between controllers at different speeds.

📄 Report: [Assignment2_Controllo_Caligiuri.pdf](./assignment_2/Assignment2_Controllo_Caligiuri.pdf)

---

## Assignment 3: Motion Planning and Control

### Description
Focuses on the synthesis of motion planning and control strategies.
- **Trajectory Planning**: Implementation of a **Frenet-based planner** for generating optimal trajectories in environments with static obstacles.
- **Control Integration**: Utilization of longitudinal (PID) and lateral (**Pure Pursuit**, **Stanley**, **MPC**) controllers for precise trajectory tracking.

### Results
In-depth analysis of trajectories, lateral errors, and side-slip angles. Comparative study of MPC vs. Pure Pursuit/Stanley in terms of precision, stability, and passenger comfort.

📄 Report: [Assignment3_Controllo.pdf](./assignment_3/Assignment3_Controllo.pdf)

---

## Directory Structure
Each assignment folder contains:
- **Source Code**: Python/C++ scripts for simulation and data analysis.
- **Results**: Visualizations, plots, and data generated during simulations.
- **Reports**: Detailed PDF documentation explaining the methodology, implementation, and experimental findings.
