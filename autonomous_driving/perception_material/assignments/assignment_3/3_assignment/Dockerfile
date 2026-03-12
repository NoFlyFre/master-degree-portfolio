# Use an official Ubuntu 18.04 base image
FROM ubuntu:18.04

# Set environment variables to configure time zone non-interactively
ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=Europe/Rome

# Install necessary packages for adding repositories and handling locales
RUN apt-get update --fix-missing && apt-get install -y \
    lsb-release \
    gnupg2 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Enable restricted, universe, and multiverse repositories
RUN sed -i 's/^# deb/deb/g' /etc/apt/sources.list && \
    sed -i 's|deb http://archive.ubuntu.com/ubuntu bionic main|deb http://archive.ubuntu.com/ubuntu bionic main restricted universe multiverse|' /etc/apt/sources.list && \
    sed -i 's|deb http://archive.ubuntu.com/ubuntu bionic-updates main|deb http://archive.ubuntu.com/ubuntu bionic-updates main restricted universe multiverse|' /etc/apt/sources.list && \
    sed -i 's|deb http://archive.ubuntu.com/ubuntu bionic-backports main|deb http://archive.ubuntu.com/ubuntu bionic-backports main restricted universe multiverse|' /etc/apt/sources.list && \
    sed -i 's|deb http://security.ubuntu.com/ubuntu bionic-security main|deb http://security.ubuntu.com/ubuntu bionic-security main restricted universe multiverse|' /etc/apt/sources.list

# Update package index
RUN apt-get update --fix-missing

# Setup your sources.list for ROS
RUN echo "deb http://packages.ros.org/ros/ubuntu $(lsb_release -sc) main" > /etc/apt/sources.list.d/ros-latest.list

# Set up your keys for ROS
RUN curl -s https://raw.githubusercontent.com/ros/rosdistro/master/ros.asc | apt-key add -

# Install ROS Melodic Desktop Full and dependencies for building ROS packages
RUN apt-get update --fix-missing && apt-get install -y \
    ros-melodic-desktop-full \
    python-rosdep \
    python-rosinstall \
    python-rosinstall-generator \
    python-wstool \
    build-essential \
    libceres-dev \
    pcl-tools \
    gdb \
    gcc \
    clang \
    ceres-solver-doc \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install additional packages for OpenGL support and X11 forwarding
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libgl1-mesa-dri \
    mesa-utils \
    xvfb \
    x11-apps \
    libx11-dev \
    && rm -rf /var/lib/apt/lists/*

# Environment setup for ROS and setting LIBGL_ALWAYS_INDIRECT globally
RUN echo "source /opt/ros/melodic/setup.bash" >> ~/.bashrc && \
    echo "export LIBGL_ALWAYS_INDIRECT=1" >> ~/.bashrc

# Initialize rosdep
RUN rosdep init && rosdep update

# Set up the ROS environment variables
ENV ROS_DISTRO melodic
ENV ROS_PYTHON_VERSION 2
ENV ROS_VERSION 1
ENV ROS_PACKAGE_PATH /opt/ros/melodic/share

# Create a workspace directory
WORKDIR /assignment3

# Set the default command to bash
CMD ["bash"]
