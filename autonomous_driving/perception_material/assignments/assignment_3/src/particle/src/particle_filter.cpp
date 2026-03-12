#include <random>
#include <algorithm>
#include <iostream>
#include <numeric>
#include <math.h>
#include <iostream>
#include <sstream>
#include <string>
#include <iterator>
#include "particle/particle_filter.h"
using namespace std;

static default_random_engine gen;

/*
 * TODO
 * This function initialize randomly the particles
 * Input:
 *  std - noise that might be added to the position
 *  nParticles - number of particles
 */
void ParticleFilter::init_random(double std[], int nParticles) {
    num_particles = nParticles;
    // Define map boundaries (adjust based on your map)
    double x_min = 0.0;
    double x_max = 10.0;
    double y_min = 0.0;
    double y_max = 10.0;

    std::uniform_real_distribution<double> dist_x(x_min, x_max);
    std::uniform_real_distribution<double> dist_y(y_min, y_max);
    std::uniform_real_distribution<double> dist_theta(-M_PI, M_PI);

    for (int i = 0; i < num_particles; ++i) {
        Particle p;
        p.x = dist_x(gen);
        p.y = dist_y(gen);
        p.theta = dist_theta(gen);
        particles.push_back(p);
    }

    is_initialized = true;
}


/*
 * TODO
 * This function initialize the particles using an initial guess
 * Input:
 *  x,y,theta - position and orientation
 *  std - noise that might be added to the position
 *  nParticles - number of particles
 */
void ParticleFilter::init(double x, double y, double theta, double std[], int nParticles) {
    num_particles = nParticles;
    // Normal distributions centered around initial guess
    std::normal_distribution<double> dist_x(x, std[0]);
    std::normal_distribution<double> dist_y(y, std[1]);
    std::normal_distribution<double> dist_theta(theta, std[2]);

    for (int i = 0; i < num_particles; ++i) {
        Particle p;
        p.x = dist_x(gen);
        p.y = dist_y(gen);
        p.theta = dist_theta(gen);
        particles.push_back(p);
    }

    is_initialized = true;
}


/*
 * TODO
 * The predict phase uses the state estimate from the previous timestep to produce an estimate of the state at the current timestep
 * Input:
 *  delta_t  - time elapsed beetween measurements
 *  std_pos  - noise that might be added to the position
 *  velocity - velocity of the vehicle
 *  yaw_rate - current orientation
 * Output:
 *  Updated x,y,theta position
 */
void ParticleFilter::prediction(double delta_t, double std_pos[], double velocity, double yaw_rate) {
    // Create normal distributions for noise
    std::normal_distribution<double> dist_x(0, std_pos[0]);
    std::normal_distribution<double> dist_y(0, std_pos[1]);
    std::normal_distribution<double> dist_theta(0, std_pos[2]);

    for (auto& particle : particles) {
        if (fabs(yaw_rate) < 1e-5) {
            // Straight line motion
            particle.x += velocity * delta_t * cos(particle.theta);
            particle.y += velocity * delta_t * sin(particle.theta);
        } else {
            // Rotational motion
            particle.x += (velocity / yaw_rate) * (sin(particle.theta + yaw_rate * delta_t) - sin(particle.theta));
            particle.y += (velocity / yaw_rate) * (-cos(particle.theta + yaw_rate * delta_t) + cos(particle.theta));
        }
	particle.theta += yaw_rate * delta_t;

	while(particle.theta > M_PI) particle.theta -= 2.0*M_PI;
	while(particle.theta < -M_PI) particle.theta += 2.0*M_PI;

        // Add Gaussian noise
        particle.x += dist_x(gen);
        particle.y += dist_y(gen);
        particle.theta += dist_theta(gen);
    }
}


/*
 * TODO
 * This function associates the landmarks from the MAP to the landmarks from the OBSERVATIONS
 * Input:
 *  mapLandmark   - landmarks of the map
 *  observations  - observations of the car
 * Output:
 *  Associated observations to mapLandmarks (perform the association using the ids)
 */
void ParticleFilter::dataAssociation(std::vector<LandmarkObs> mapLandmarks, std::vector<LandmarkObs>& observations) {
    for (auto& obs : observations) {
        double min_distance = std::numeric_limits<double>::max();
        int map_id = -1;

        for (const auto& landmark : mapLandmarks) {
            double distance = dist(obs.x, obs.y, landmark.x, landmark.y);
            if (distance < min_distance) {
                min_distance = distance;
                map_id = landmark.id;
            }
        }
        obs.id = map_id;
    }
}


/*
 * TODO
 * This function transform a local (vehicle) observation into a global (map) coordinates
 * Input:
 *  observation   - A single landmark observation
 *  p             - A single particle
 * Output:
 *  local         - transformation of the observation from local coordinates to global
 */
LandmarkObs transformation(LandmarkObs observation, Particle p)
{
    LandmarkObs global;
    global.id = observation.id;
    global.x = p.x + (cos(p.theta) * observation.x - sin(p.theta) * observation.y);
    global.y = p.y + (sin(p.theta) * observation.x + cos(p.theta) * observation.y);
    return global;
}

/*
 * TODO
 * This function updates the weights of each particle
 * Input:
 *  std_landmark   - Sensor noise
 *  observations   - Sensor measurements
 *  map_landmarks  - Map with the landmarks
 * Output:
 *  Updated particle's weight (particles[i].weight *= w)
 */
void ParticleFilter::updateWeights(double std_landmark[], std::vector<LandmarkObs> observations, Map map_landmarks) {
    for (auto& particle : particles) {
        // Step 1: Transform observations
        std::vector<LandmarkObs> transformed_observations;
        for (const auto& obs : observations) {
            transformed_observations.push_back(transformation(obs, particle));
        }

        // Step 2: Associate observations with landmarks
        std::vector<LandmarkObs> mapLandmarks;
        for (const auto& lm : map_landmarks.landmark_list) {
            mapLandmarks.push_back(LandmarkObs{lm.id_i, lm.x_f, lm.y_f});
        }
        dataAssociation(mapLandmarks, transformed_observations);

        // Step 3: Update weights
        particle.weight = 1.0;
        double sigma_x = std_landmark[0];
        double sigma_y = std_landmark[1];
        double gauss_norm = 1 / (2 * M_PI * sigma_x * sigma_y);

        for (const auto& obs : transformed_observations) {
            // Find associated landmark
            LandmarkObs landmark;
            for (const auto& lm : mapLandmarks) {
                if (lm.id == obs.id) {
                    landmark = lm;
                    break;
                }
            }

            // Calculate weight
            double dx = obs.x - landmark.x;
            double dy = obs.y - landmark.y;
            double exponent = (dx * dx) / (2 * sigma_x * sigma_x) + (dy * dy) / (2 * sigma_y * sigma_y);
            double weight = gauss_norm * exp(-exponent);

            // Multiply particle's weight
            particle.weight *= weight;
        }
    }
}


/*
 * TODO
 * This function resamples the set of particles by repopulating the particles using the weight as metric
 */
void ParticleFilter::resample() {
    std::vector<Particle> new_particles;
    std::vector<double> weights;
    for (const auto& particle : particles) {
        weights.push_back(particle.weight);
    }

    std::uniform_real_distribution<double> dist_double(0.0, *max_element(weights.begin(), weights.end()));
    std::uniform_int_distribution<int> dist_int(0, num_particles - 1);

    int index = dist_int(gen);
    double beta = 0.0;

    for (int i = 0; i < num_particles; ++i) {
        beta += dist_double(gen) * 2.0;
        while (beta > weights[index]) {
            beta -= weights[index];
            index = (index + 1) % num_particles;
        }
        new_particles.push_back(particles[index]);
    }
    particles = new_particles;
}

