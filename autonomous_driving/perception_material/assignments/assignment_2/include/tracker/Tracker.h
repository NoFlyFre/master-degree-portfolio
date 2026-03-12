#ifndef TRACKER_H_
#define TRACKER_H_

#include "tracker/Tracklet.h"
#include <limits>

class Tracker
{
public:
  Tracker();
  ~Tracker();

  // handle tracklets
  void removeTracks();
  void addTracks(const std::vector<bool> &associated_detections,
                 const std::vector<double> &centroids_x,
                 const std::vector<double> &centroids_y);

  // associate tracklets and detections
  void dataAssociation(std::vector<bool> &associated_detections,
                       const std::vector<double> &centroids_x,
                       const std::vector<double> &centroids_y);

  // track objects
  void track(const std::vector<double> &centroids_x,
             const std::vector<double> &centroids_y,
             bool lidarStatus);

  // getters
  const std::vector<Tracklet> &getTracks() { return tracks_; }

  struct TrackStats {
      int id;
      double distance;
  };

  TrackStats getLongestTrack() const {
      TrackStats result{-1, 0.0};
      for (const auto& track : tracks_) {
          if (track.getTotalDistance() > result.distance) {
              result.id = track.getId();
              result.distance = track.getTotalDistance();
          }
      }
      return result;
  }

private:
  // tracklets
  std::vector<Tracklet> tracks_;
  int cur_id_;

  // association
  std::vector<std::pair<int, int>> associated_track_det_ids_;

  // thresholds
  double distance_threshold_;
  double covariance_threshold;
  int loss_threshold;
};

#endif // TRACKER_H_
