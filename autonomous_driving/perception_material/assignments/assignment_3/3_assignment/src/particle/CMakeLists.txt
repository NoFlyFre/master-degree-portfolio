cmake_minimum_required(VERSION 3.0.2)
project(particle)

## Imposta lo standard C++14
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

## Trova i pacchetti catkin e le librerie
find_package(catkin REQUIRED COMPONENTS
  nav_msgs
  roscpp
  std_msgs
  pcl_ros
  pcl_conversions
  sensor_msgs
)

find_package(PCL REQUIRED)
find_package(Ceres REQUIRED)
find_package(gflags REQUIRED)

## Configurazione specifica di catkin
catkin_package(
  INCLUDE_DIRS include
#  LIBRARIES particle
  CATKIN_DEPENDS nav_msgs roscpp
#  DEPENDS system_lib
)

## Specifica le directory di include aggiuntive
include_directories(
  include
  ${catkin_INCLUDE_DIRS}
  ${PCL_INCLUDE_DIRS}
  ${CERES_INCLUDE_DIRS}
  ${GFLAGS_INCLUDE_DIRS}
)

## Dichiara un eseguibile C++
add_executable(${PROJECT_NAME}_node src/Renderer.cpp src/particle_filter.cpp src/main.cpp)

## Specifica le librerie per il linking
target_link_libraries(${PROJECT_NAME}_node
    ${catkin_LIBRARIES}
    ${PCL_LIBRARIES}
    ${CERES_LIBRARIES}
    ${GFLAGS_LIBRARIES}
)

## Installa l'eseguibile
install(TARGETS ${PROJECT_NAME}_node
  RUNTIME DESTINATION ${CATKIN_PACKAGE_BIN_DESTINATION}
)