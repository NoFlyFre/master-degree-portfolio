#include "tracker/Tracker.h"

Tracker::Tracker()
{
    cur_id_ = 0;
    distance_threshold_ = 0.5;    // 0.5 metri di soglia per l'associazione
    covariance_threshold = 1.0;   // soglia per la covarianza
    loss_threshold = 10;          // numero massimo di frame persi prima di eliminare una traccia
}
Tracker::~Tracker()
{
}

/*
    This function removes tracks based on any strategy
*/
void Tracker::removeTracks()
{
    std::vector<Tracklet> tracks_to_keep;

    for (size_t i = 0; i < tracks_.size(); ++i)
    {
        // Rimuovi le tracce che non sono state aggiornate per troppo tempo
        if (tracks_[i].getLossCount() < loss_threshold)
        {
            tracks_to_keep.push_back(tracks_[i]);
        }
    }

    tracks_.swap(tracks_to_keep);
}

/*
    This function add new tracks to the set of tracks ("tracks_" is the object that contains this)
*/
void Tracker::addTracks(const std::vector<bool> &associated_detections, const std::vector<double> &centroids_x, const std::vector<double> &centroids_y)
{
    // Adding not associated detections
    for (size_t i = 0; i < associated_detections.size(); ++i)
        if (!associated_detections[i])
            tracks_.push_back(Tracklet(cur_id_++, centroids_x[i], centroids_y[i]));
}

/*
    This function associates detections (centroids_x,centroids_y) with the tracks (tracks_)
    Input:
        associated_detection an empty vector to host the associated detection
        centroids_x & centroids_y measurements representing the detected objects
*/
void Tracker::dataAssociation(std::vector<bool> &associated_detections, const std::vector<double> &centroids_x, const std::vector<double> &centroids_y)
{
    associated_track_det_ids_.clear();

    for (size_t i = 0; i < tracks_.size(); ++i)
    {
        int closest_point_id = -1;
        double min_dist = std::numeric_limits<double>::max();

        for (size_t j = 0; j < associated_detections.size(); ++j)
        {
            if (!associated_detections[j])
            {
                // Calcola la distanza euclidea tra la traccia e la rilevazione
                double dx = tracks_[i].getX() - centroids_x[j];
                double dy = tracks_[i].getY() - centroids_y[j];
                double dist = std::sqrt(dx * dx + dy * dy);

                // Aggiorna il punto più vicino se la distanza è minore
                if (dist < min_dist)
                {
                    min_dist = dist;
                    closest_point_id = j;
                }
            }
        }

        // Associa la rilevazione più vicina alla traccia se entro la soglia
        if (min_dist < distance_threshold_ && closest_point_id >= 0)
        {
            associated_track_det_ids_.push_back(std::make_pair(closest_point_id, i));
            associated_detections[closest_point_id] = true;
        }
    }
}

void Tracker::track(const std::vector<double> &centroids_x,
                    const std::vector<double> &centroids_y,
                    bool lidarStatus)
{
    std::vector<bool> associated_detections(centroids_x.size(), false);

    // Predict the position
    for (auto &track : tracks_)
    {
        track.predict();
    }

    // Associate the predictions with the detections
    dataAssociation(associated_detections, centroids_x, centroids_y);

    // Update tracklets with the new detections
    for (int i = 0; i < associated_track_det_ids_.size(); ++i)
    {
        auto det_id = associated_track_det_ids_[i].first;
        auto track_id = associated_track_det_ids_[i].second;
        tracks_[track_id].update(centroids_x[det_id], centroids_y[det_id], lidarStatus);
    }

    // Remove dead tracklets
    removeTracks();

    // Add new tracklets
    addTracks(associated_detections, centroids_x, centroids_y);
}
