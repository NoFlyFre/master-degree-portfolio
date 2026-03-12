// Include delle librerie necessarie per PCL e librerie standard
#include <iomanip>
#include <pcl/ModelCoefficients.h>
#include <pcl/point_types.h>
#include <pcl/io/pcd_io.h>
#include <pcl/filters/extract_indices.h>
#include <pcl/filters/voxel_grid.h>
#include <pcl/features/normal_3d.h>
#include <pcl/kdtree/kdtree.h>
#include <pcl/sample_consensus/method_types.h>
#include <pcl/sample_consensus/model_types.h>
#include <pcl/segmentation/sac_segmentation.h>
#include <pcl/segmentation/extract_clusters.h>
#include <pcl/filters/crop_box.h>
#include "../include/Renderer.hpp"
#include <pcl/common/common.h>
#include <chrono>
#include <unordered_set>
#include "../include/tree_utilities.hpp"
#include <boost/filesystem.hpp>

// Definizione per utilizzare le funzioni PCL per il clustering; se commentato, utilizza l'implementazione personalizzata
#define USE_PCL_LIBRARY

using namespace lidar_obstacle_detection;

// Alias per un insieme non ordinato di interi, usato per tracciare i punti visitati
typedef std::unordered_set<int> my_visited_set_t;

// Funzione per impostare un KD-Tree personalizzato utilizzando la nuvola di punti
void setupKdtree(typename pcl::PointCloud<pcl::PointXYZ>::Ptr cloud, my_pcl::KdTree *tree, int dimension)
{
    // Inserisce ogni punto della nuvola nel KD-Tree
    for (int i = 0; i < cloud->size(); ++i)
    {
        tree->insert({cloud->at(i).x, cloud->at(i).y, cloud->at(i).z}, i);
    }
}

// Funzione di prossimità personalizzata per il clustering (Assegnamento opzionale)
void proximity(pcl::PointCloud<pcl::PointXYZ>::Ptr cloud, int index, my_pcl::KdTree *tree,
               float distanceTol, std::unordered_set<int> &processed, std::vector<int> &cluster, int maxSize)
{
    // Aggiunge l'indice corrente al cluster
    cluster.push_back(index);
    // Marca il punto come processato
    processed.insert(index);

    // Ottiene il punto corrente dalla nuvola
    std::vector<float> point = {cloud->points[index].x, cloud->points[index].y, cloud->points[index].z};

    // Cerca i punti vicini entro la tolleranza di distanza
    std::vector<int> nearbyPoints = tree->search(point, distanceTol);

    // Itera attraverso i punti vicini
    for (int i : nearbyPoints)
    {
        // Se il punto non è stato ancora processato
        if (processed.find(i) == processed.end())
        {
            // Controlla se il cluster ha raggiunto la dimensione massima
            if (cluster.size() >= maxSize)
            {
                return; // Esce se il cluster è troppo grande
            }
            // Chiama ricorsivamente proximity sul punto vicino
            proximity(cloud, i, tree, distanceTol, processed, cluster, maxSize);
        }
    }
}

// Funzione di clustering euclideo personalizzata (Assegnamento opzionale)
std::vector<pcl::PointIndices> euclideanCluster(pcl::PointCloud<pcl::PointXYZ>::Ptr cloud, my_pcl::KdTree *tree,
                                                float distanceTol, int minSize, int maxSize)
{
    // Vettore per memorizzare i cluster risultanti
    std::vector<pcl::PointIndices> clusters;
    // Insieme per tenere traccia dei punti già processati
    std::unordered_set<int> processed;

    // Itera su tutti i punti nella nuvola
    for (int i = 0; i < cloud->points.size(); ++i)
    {
        // Se il punto non è stato ancora processato
        if (processed.find(i) == processed.end())
        {
            // Crea un nuovo cluster
            std::vector<int> cluster;
            // Usa la funzione proximity per espandere il cluster
            proximity(cloud, i, tree, distanceTol, processed, cluster, maxSize);

            // Se la dimensione del cluster è entro i limiti specificati
            if (cluster.size() >= minSize && cluster.size() <= maxSize)
            {
                // Crea un oggetto PointIndices per il cluster
                pcl::PointIndices cluster_indices;
                cluster_indices.indices = cluster;
                // Aggiunge il cluster alla lista dei cluster
                clusters.push_back(cluster_indices);
            }
        }
    }

    // Ritorna il vettore dei cluster
    return clusters;
}

// Funzione per elaborare la nuvola di punti e renderizzare i risultati
void ProcessAndRenderPointCloud(Renderer &renderer, pcl::PointCloud<pcl::PointXYZ>::Ptr &cloud)
{
    // 1) Downsampling del dataset utilizzando un filtro Voxel Grid
    pcl::PointCloud<pcl::PointXYZ>::Ptr cloud_filtered(new pcl::PointCloud<pcl::PointXYZ>());
    pcl::VoxelGrid<pcl::PointXYZ> vg;
    vg.setInputCloud(cloud);
    vg.setLeafSize(0.1f, 0.1f, 0.1f); // Imposta la dimensione del voxel
    vg.filter(*cloud_filtered);

    // 2) Ritaglia la nuvola di punti per concentrarsi sull'area di interesse
    pcl::CropBox<pcl::PointXYZ> cb(true);
    cb.setInputCloud(cloud_filtered);
    cb.setMin(Eigen::Vector4f(-20, -6, -2, 1));
    cb.setMax(Eigen::Vector4f(30, 7, 5, 1));
    cb.filter(*cloud_filtered);

    // 3) Segmentazione del piano (strada) utilizzando RANSAC
    pcl::PointIndices::Ptr inliers(new pcl::PointIndices);
    pcl::ModelCoefficients::Ptr coefficients(new pcl::ModelCoefficients);
    pcl::SACSegmentation<pcl::PointXYZ> seg;
    seg.setOptimizeCoefficients(true);           // Opzionale
    seg.setModelType(pcl::SACMODEL_PLANE);       // Tipo di modello: piano
    seg.setMethodType(pcl::SAC_RANSAC);          // Utilizza il metodo RANSAC
    seg.setDistanceThreshold(0.2);               // Tolleranza di distanza
    seg.setInputCloud(cloud_filtered);
    seg.segment(*inliers, *coefficients);

    // Controlla se è stato trovato un piano
    if (inliers->indices.size() == 0)
    {
        std::cout << "Non è stato possibile stimare un modello planare per il dataset fornito." << std::endl;
        return;
    }

    // 4) Estrai il piano e gli oggetti (tutto il resto)
    pcl::PointCloud<pcl::PointXYZ>::Ptr cloud_plane(new pcl::PointCloud<pcl::PointXYZ>);
    pcl::PointCloud<pcl::PointXYZ>::Ptr cloud_objects(new pcl::PointCloud<pcl::PointXYZ>);
    pcl::ExtractIndices<pcl::PointXYZ> extract;

    // Estrai il piano
    extract.setInputCloud(cloud_filtered);
    extract.setIndices(inliers);
    extract.setNegative(false); // False per estrarre il piano
    extract.filter(*cloud_plane);

    // Estrai gli oggetti
    extract.setNegative(true); // True per estrarre tutto tranne il piano
    extract.filter(*cloud_objects);

    // 5) Crea il KD-Tree per il clustering
    pcl::search::KdTree<pcl::PointXYZ>::Ptr tree(new pcl::search::KdTree<pcl::PointXYZ>);
    tree->setInputCloud(cloud_objects);

    // Variabili per i parametri di clustering
    std::vector<pcl::PointIndices> cluster_indices;
    float clusterTolerance = 0.2;  // Tolleranza per il clustering
    int setMinClusterSize = 50;    // Dimensione minima del cluster
    int setMaxClusterSize = 25000; // Dimensione massima del cluster

    // 6) Esegui il clustering euclideo
#ifdef USE_PCL_LIBRARY

    // Utilizzo delle funzioni integrate di PCL per il clustering
    pcl::EuclideanClusterExtraction<pcl::PointXYZ> ec;
    ec.setClusterTolerance(clusterTolerance);
    ec.setMinClusterSize(setMinClusterSize);
    ec.setMaxClusterSize(setMaxClusterSize);
    ec.setSearchMethod(tree);
    ec.setInputCloud(cloud_objects);
    ec.extract(cluster_indices);

#else
    // Assegnamento opzionale: Utilizzo dell'implementazione personalizzata del clustering
    my_pcl::KdTree treeM;
    treeM.set_dimension(3);
    setupKdtree(cloud_objects, &treeM, 3);
    cluster_indices = euclideanCluster(cloud_objects, &treeM, clusterTolerance, setMinClusterSize, setMaxClusterSize);
#endif

    // 7) Renderizza i cluster e il piano senza renderizzare la nuvola originale
    renderer.RenderPointCloud(cloud_plane, "planeCloud", Color(0, 1, 0)); // Renderizza il piano in verde

    // Definisci i colori per renderizzare i cluster
    std::vector<Color> colors = {Color(1, 1, 0), Color(0, 0, 1), Color(1, 0, 1), Color(0, 1, 1)};

    int clusterId = 0;
    // Itera su ogni cluster
    for (std::vector<pcl::PointIndices>::const_iterator it = cluster_indices.begin(); it != cluster_indices.end(); ++it)
    {
        // Crea una nuova nuvola di punti per il cluster
        pcl::PointCloud<pcl::PointXYZ>::Ptr cloud_cluster(new pcl::PointCloud<pcl::PointXYZ>);
        for (std::vector<int>::const_iterator pit = it->indices.begin(); pit != it->indices.end(); ++pit)
            cloud_cluster->push_back((*cloud_objects)[*pit]);
        cloud_cluster->width = cloud_cluster->size();
        cloud_cluster->height = 1;
        cloud_cluster->is_dense = true;

        // Renderizza il cluster con un colore specifico
        renderer.RenderPointCloud(cloud_cluster, "cluster" + std::to_string(clusterId), colors[clusterId % colors.size()]);

        // 8) Calcola la distanza di ogni cluster dal veicolo ego
        Eigen::Vector4f centroid;
        pcl::compute3DCentroid(*cloud_cluster, centroid);
        float distance = sqrt(centroid[0] * centroid[0] + centroid[1] * centroid[1]);

        // Stampa la distanza sulla console
        std::cout << "Cluster " << clusterId << " a una distanza di: " << distance << " metri." << std::endl;

        // Aggiungi testo nella visualizzazione per mostrare la distanza
        std::ostringstream oss;
        oss << std::fixed << std::setprecision(2) << distance;
        renderer.addText(centroid[0], centroid[1], centroid[2], oss.str() + " m");

        // 9) Colora in rosso i veicoli che sono sia davanti che entro 5 metri dal veicolo ego
        pcl::PointXYZ minPt, maxPt;
        pcl::getMinMax3D(*cloud_cluster, minPt, maxPt);
        Box box{minPt.x, minPt.y, minPt.z,
                maxPt.x, maxPt.y, maxPt.z};

        // Controlla se il cluster è davanti e entro 5 metri
        if (centroid[0] > 0 || distance <= 5.0)
        {
            // Renderizza la bounding box in rosso
            renderer.RenderBox(box, clusterId, Color(1, 0, 0));
        }
        else
        {
            // Renderizza la bounding box con il colore del cluster
            renderer.RenderBox(box, clusterId, colors[clusterId % colors.size()]);
        }

        ++clusterId;
    }
}

int main(int argc, char *argv[])
{
    // Inizializza il renderer e le impostazioni della telecamera
    Renderer renderer;
    renderer.InitCamera(CameraAngle::XY);
    // Svuota il visualizzatore
    renderer.ClearViewer();

    // Puntatore per contenere la nuvola di punti di input
    pcl::PointCloud<pcl::PointXYZ>::Ptr input_cloud(new pcl::PointCloud<pcl::PointXYZ>);

    // Utilizza Boost filesystem per leggere la directory del dataset
    namespace fs = boost::filesystem;
    std::vector<fs::path> stream(fs::directory_iterator{"/Users/fraca/Desktop/UNI/Informatica/Magistrale/1 anno/AD/Materiale Perception/Assignments/assignment_1/dataset_2"},
                                 fs::directory_iterator{});

    // Ordina i file in ordine crescente (cronologico)
    std::sort(stream.begin(), stream.end());

    // Iteratore per scorrere il dataset
    auto streamIterator = stream.begin();

    // Loop principale per elaborare le nuvole di punti
    while (not renderer.WasViewerStopped())
    {
        // Svuota il visualizzatore per il frame successivo
        renderer.ClearViewer();

        // Legge la prossima nuvola di punti dal dataset
        pcl::PCDReader reader;
        reader.read((*streamIterator).string(), *input_cloud);

        // Misura il tempo di elaborazione
        auto startTime = std::chrono::steady_clock::now();

        // Elabora la nuvola di punti e renderizza i risultati
        ProcessAndRenderPointCloud(renderer, input_cloud);

        auto endTime = std::chrono::steady_clock::now();
        auto elapsedTime = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime);

        // Output del tempo di elaborazione e informazioni sulla nuvola di punti
        std::cout << "[PointCloudProcessor<PointT>::ReadPcdFile] Caricati "
                  << input_cloud->points.size() << " punti dati da " << (*streamIterator).string() << ". Segmentazione del piano ha impiegato " << elapsedTime.count() << " millisecondi." << std::endl;

        // Passa alla prossima nuvola di punti nel dataset
        streamIterator++;
        if (streamIterator == stream.end())
            streamIterator = stream.begin();

        // Renderizza il visualizzatore
        renderer.SpinViewerOnce();
    }
}