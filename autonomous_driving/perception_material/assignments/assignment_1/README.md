# Assignment 1: Euclidean Clustering for Object Detection

This directory contains the implementation of a Euclidean Clustering pipeline utilizing the **Point Cloud Library (PCL)** to segment and detect cars and pedestrians in a LiDAR point cloud dataset.

## Introduction
The goal of this assignment is to develop an efficient object detection system that can:
1. **Implement Euclidean Clustering** as taught in the course.
2. **Pre-process Data**: Downsample and crop the raw LiDAR points.
3. **Segment Ground Plane**: Utilize RANSAC to remove the road surface.
4. **Detect Objects**: Group remaining points into distinct clusters.

## Implementation Details

### 1. Data Pre-processing
- **Downsampling**: Used the `VoxelGrid` filter to reduce point cloud density (Voxel size: `0.1f`).
- **Cropping**: Applied a `CropBox` filter to focus on the Region of Interest (ROI) in front of the ego vehicle (Min: `-20, -6, -2`, Max: `30, 7, 5`).

### 2. Ground Plane Segmentation
- **RANSAC Plane Segmentation**: Implemented RANSAC to segment the road plane with a distance threshold of `0.2`.
- **Inlier/Outlier Extraction**: Extracted points belonging to the ground (inliers) and potential objects (outliers) using `ExtractIndices`.

### 3. Euclidean Clustering
- **Parameter Optimization**: Finetuned parameters on Dataset 2 (higher complexity) and validated them on Dataset 1.
- **PCL Implementation**: Configured `EuclideanClusterExtraction` with:
    - **Cluster Tolerance**: `0.2`
    - **Min Cluster Size**: `50`
    - **Max Cluster Size**: `25000`
- **Custom Clustering (Optional)**: Implemented `proximity` and `euclideanCluster` functions from scratch to verify the underlying algorithm. This can be toggled via the `USE_PCL_LIBRARY` flag.

### 4. Visualization and Rendering
- **Distance Calculation**: For each cluster, the distance relative to the ego vehicle is calculated using its centroid.
- **Color Coding**: Each cluster is rendered in a different color. Vehicles detected within 5 meters of the ego vehicle are highlighted with a **red bounding box**.
- **On-Screen Display**: Distances are rendered directly in the 3D scene and printed to the console.

## Building and Running

### Prerequisites
- **OS**: macOS / Linux
- **Dependencies**: PCL (Point Cloud Library), Boost, CMake

### Compilation
```bash
mkdir build
cd build
cmake ..
make
```

### Execution
```bash
./cluster_extraction
```

**Note**: Ensure that the dataset path in the source code points to your local PCD files directory.
