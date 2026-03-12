# Assignment 2: Kalman Filter Multi-Object Tracker

This project implements a multi-object tracking system using a **Kalman Filter** and **LiDAR** point cloud data.

## Features

### Core Tracking
- **KalmanFilter.cpp**: Full implementation of the Kalman Filter, including optimized P-matrix initialization and predict/update functions.
- **Tracker.cpp**: Handles data association between clusters and tracklets, initialization, and removal of tracks based on frames lost.

### Advanced Metrics
- **Longest Path Tracker**: Calculates and displays the cumulative distance traveled by each track.
- **Real-time Visualization**: Identifies the track with the longest distance traveled in real-time.
- **Custom IDs**: Track IDs are rendered with an offset (e.g., +1000) for better visibility during debugging.

## Implementation Details

### Distance Tracking
The distance is updated cumulatively during each LiDAR update for active tracklets:
```cpp
void Tracklet::update(double x, double y, bool lidarStatus) {
  if (lidarStatus) {
    if (!first_update_) {
      double dx = x - last_x_;
      double dy = y - last_y_;
      total_distance_ += std::sqrt(dx*dx + dy*dy);
    }
    // ...
  }
}
```

## Building and Running

### Compilation
```bash
mkdir build
cd build
cmake ..
make -j2
```

### Execution
1. Place the `log` dataset folder in the build directory.
2. Run the executable:
```bash
./main
```
