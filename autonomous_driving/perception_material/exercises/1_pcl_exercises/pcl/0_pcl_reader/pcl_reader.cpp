#include <pcl/visualization/pcl_visualizer.h>
#include <iostream>
#include <pcl/io/io.h>
#include <pcl/io/pcd_io.h>
#include <thread>  // Necessario per std::this_thread::sleep_for

int user_data;

void viewerPsycho(pcl::visualization::PCLVisualizer& viewer) {
    static unsigned count = 0;
    std::stringstream ss;
    ss << "Once per viewer loop: " << count++;
    viewer.removeShape("text", 0);
    viewer.addText(ss.str(), 200, 300, "text", 0);
    
    // Incrementa user_data
    user_data++;
}

int main(int argc, char **argv) {
    pcl::PointCloud<pcl::PointXYZ>::Ptr cloud(new pcl::PointCloud<pcl::PointXYZ>);

    if (argc < 2) {
        std::cerr << "Usage: " << argv[0] << " <path_to_pcd_file>" << std::endl;
        return -1;
    }

    if (pcl::io::loadPCDFile(argv[1], *cloud) == -1) {
        std::cerr << "Couldn't read file " << argv[1] << std::endl;
        return -1;
    }

    pcl::visualization::PCLVisualizer viewer("PCL Visualizer");

    // Aggiungi la point cloud al visualizzatore
    viewer.addPointCloud(cloud, "sample cloud");

    // Mantiene il ciclo di visualizzazione
    while (!viewer.wasStopped()) {
        // Chiama viewerPsycho direttamente su PCLVisualizer
        viewerPsycho(viewer);
        
        // Esegui il rendering e aspetta
        viewer.spinOnce(100);
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    return 0;
}