#include "tracker/KalmanFilter.h"

KalmanFilter::KalmanFilter()
{
}

KalmanFilter::~KalmanFilter()
{
}

void KalmanFilter::init(double dt)
{
  dt_ = dt;

  // create a 4D state vector
  x_ = Eigen::VectorXd(4);

  // Initialize the state covariance matrix P
  P_ = Eigen::MatrixXd(4, 4);
  P_ << 1.0, 0.0, 0.0, 0.0,     // incertezza posizione x
      0.0, 1.0, 0.0, 0.0,       // incertezza posizione y
      0.0, 0.0, 100.0, 0.0,     // incertezza velocità x
      0.0, 0.0, 0.0, 100.0;     // incertezza velocità y

  // measurement covariance
  R_ = Eigen::MatrixXd(2, 2);
  R_ << 0.0225, 0.,
      0., 0.0225;

  // measurement matrix
  H_ = Eigen::MatrixXd(2, 4);
  H_ << 1., 0., 0., 0.,
      0., 1., 0., 0.;

  // the transition matrix F
  F_ = Eigen::MatrixXd(4, 4);
  F_ << 1., 0., dt_, 0.,
      0., 1., 0., dt_,
      0., 0., 1., 0.,
      0., 0., 0., 1.;

  // set the acceleration noise components
  double noise_ax_ = 2.;
  double noise_ay_ = 2.;

  double dt_2 = dt_ * dt_;
  double dt_3 = dt_2 * dt_;
  double dt_4 = dt_3 * dt_;

  // set the process covariance matrix Q
  Q_ = Eigen::MatrixXd(4, 4);
  Q_ << dt_4 / 4. * noise_ax_, 0., dt_3 / 2. * noise_ax_, 0.,
      0., dt_4 / 4. * noise_ay_, 0., dt_3 / 2. * noise_ay_,
      dt_3 / 2. * noise_ax_, 0., dt_2 * noise_ax_, 0.,
      0., dt_3 / 2. * noise_ay_, 0., dt_2 * noise_ay_;
}

void KalmanFilter::predict()
{
  // Predizione dello stato
  x_ = F_ * x_;
  
  // Aggiornamento della covarianza
  P_ = F_ * P_ * F_.transpose() + Q_;
}

void KalmanFilter::update(const Eigen::VectorXd &z)
{
  // Calcolo dell'innovazione
  Eigen::VectorXd y = z - H_ * x_;
  
  // Calcolo della matrice di covarianza dell'innovazione
  Eigen::MatrixXd S = H_ * P_ * H_.transpose() + R_;
  
  // Calcolo del guadagno di Kalman
  Eigen::MatrixXd K = P_ * H_.transpose() * S.inverse();
  
  // Aggiornamento dello stato
  x_ = x_ + K * y;
  
  // Aggiornamento della covarianza
  Eigen::MatrixXd I = Eigen::MatrixXd::Identity(x_.size(), x_.size());
  P_ = (I - K * H_) * P_;
}

void KalmanFilter::setState(double x, double y)
{
  x_ << x, y, 0., 0.;
}