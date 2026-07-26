# 🚗 Autonomous Driving

![Grade](https://img.shields.io/badge/Grade-27%2F30-green)
![CFU](https://img.shields.io/badge/CFU-6-blue)
![C++](https://img.shields.io/badge/C%2B%2B-00599C?logo=cplusplus&logoColor=white)
![Python](https://img.shields.io/badge/Python-3776AB?logo=python&logoColor=white)
![ROS](https://img.shields.io/badge/ROS-22314E?logo=ros&logoColor=white)
![PCL](https://img.shields.io/badge/PCL-Point%20Cloud%20Library-orange)

*Platforms and Algorithms for Autonomous Systems — 6 CFU · A.Y. 2024/2025 · **27/30***

Six graded assignments across the two halves of the autonomous stack: **deciding where to go**
(planning and control) and **understanding what's out there** (perception and state estimation).
Every assignment ships with source code, simulation results and a written technical report.

## ⚙️ [Planning & Control](./control_material/)

| # | Assignment | What I built |
|---|---|---|
| 1 | **Vehicle Modeling & Simulation** | Kinematic, linear and non-linear vehicle models compared under sinusoidal and constant steering inputs, plus an Euler vs **RK4** numerical integration study across time steps |
| 2 | **Motion Control** | Longitudinal **PID** speed tracking; lateral control with **Pure Pursuit** and **Stanley** at 10–20 m/s; **curvature-based lookahead** for high-speed control at 23–25 m/s, analysing lateral error and steering oscillation |
| 3 | **Motion Planning & Control** | A **Frenet-frame trajectory planner** for optimal paths around static obstacles, integrated with PID + Pure Pursuit / Stanley / **MPC** tracking. Comparative study of MPC against the geometric controllers on precision, stability and passenger comfort (side-slip angle) |

Also in [`exercises/`](./control_material/exercises/) and
[`support_material/`](./control_material/support_material/): practice simulations and vehicle model
references.

## 👁️ [Perception & State Estimation](./perception_material/)

| # | Assignment | What I built |
|---|---|---|
| 1 | **Euclidean Clustering for Object Detection** | A **PCL** pipeline detecting cars and pedestrians in LiDAR point clouds: `VoxelGrid` downsampling, `CropBox` ROI extraction, **RANSAC** ground plane segmentation, then Euclidean clustering implemented from scratch |
| 2 | **Kalman Filter Multi-Object Tracker** | A full **Kalman Filter** in C++ with tuned P-matrix initialisation, plus a `Tracker` handling cluster-to-tracklet data association, track birth and death. Extended with cumulative distance metrics and real-time longest-path highlighting |
| 3 | **Particle Filter Localisation** | A **Particle Filter** localising a forklift from LiDAR and landmark references on **ROS** — prediction, weight update and resampling — with an experimental study of parameter tuning against localisation accuracy |

Also in [`exercises/`](./perception_material/exercises/): PCL filtering/segmentation/clustering drills,
1D and 2D Kalman Filter implementations, and particle filter / SLAM localisation exercises.

## 🎯 Skills demonstrated

Turning sensor noise into decisions: probabilistic state estimation, 3D point cloud processing, and
control loops that stay stable when the vehicle is moving fast enough that the geometry stops being
forgiving. Written in C++ and Python against ROS and PCL, with every design choice backed by measured
results rather than intuition.
