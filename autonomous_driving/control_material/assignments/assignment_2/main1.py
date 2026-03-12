import numpy as np
import matplotlib.pyplot as plt
import os

from simulation import Simulation
import pid
import purepursuit
import stanley
from mpc import *
import cubic_spline_planner
import math

# Nome dell'esercizio e creazione directory risultati
exercise_name = "Exercise_1"
save_path = "Results/" + exercise_name + "/"
os.makedirs(save_path, exist_ok=True)

# Parametri di simulazione
dt = 0.001
ax = 0.0
steer = 0.0
sim_time = 40.0
steps = int(sim_time / dt)

# Riferimento di velocità
target_speed = 15.0

# Parametri veicolo
lf = 1.156
lr = 1.42
wheelbase = lf + lr
mass = 1200
Iz = 1792
max_steer = 3.14

# PID longitudinale
long_control_pid = pid.PIDController(kp=1.5, ki=0.6, kd=0.06, output_limits=(-2, 2))

# Parametri per la parte laterale
# (anche se in Exercise 1 si suppone steering=0, li lasciamo qui se servono)
k_pp = 0.001
look_ahead = 1.0
k_stanley = 0.001
pp_controller = purepursuit.PurePursuitController(wheelbase, max_steer)
stanley_controller = stanley.StanleyController(k_stanley, lf, max_steer)

def load_path(file_path):
    """
    Carica la path dal file (non serve davvero in Exercise 1 se non giriamo).
    """
    with open(file_path, "r") as file:
        xs, ys = [], []
        while file.readline():
            line = file.readline()
            if not line.strip():
                break
            xs.append(float(line.split(",")[0]))
            ys.append(float(line.split(",")[1]))
    return xs, ys

# In Exercise 1 tipicamente usiamo steering=0. 
# Ma se vuoi caricare la path e vedere se sterzi... ecco come.
xs, ys = load_path("oval_trj.txt")
path_spline = cubic_spline_planner.Spline2D(xs, ys)

def plot_and_save(xdata_list, ydata_list, labels, title, xlabel, ylabel, filename):
    """
    Funzione di supporto per plottare e salvare in Results/Exercise_1/
    """
    plt.figure(figsize=(10,6))
    for i in range(len(xdata_list)):
        plt.plot(xdata_list[i], ydata_list[i], label=labels[i])
    plt.title(title)
    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    plt.legend()
    plt.grid(True)
    plt.savefig(save_path + filename, dpi=300, bbox_inches="tight")
    plt.close()
    
def plot_trajectory(x_vals, y_vals, labels, path_spline, filename="trajectory.png"):
    """
    Plot 2D trajectory (x vs y) + path_spline.
    """
    plt.figure(figsize=(10, 6))
    # Plot i vari x_vals, y_vals
    for i in range(len(x_vals)):
        plt.plot(x_vals[i], y_vals[i], label=labels[i])

    # Traccia spline
    spline_x = []
    spline_y = []
    s_sample = np.linspace(0, path_spline.s[-1], 1000)
    for s in s_sample:
        px, py = path_spline.calc_position(s)
        spline_x.append(px)
        spline_y.append(py)
    plt.plot(spline_x, spline_y, "--r", label="Path Spline")

    plt.title("2D Trajectory Comparison")
    plt.xlabel("X (m)")
    plt.ylabel("Y (m)")
    plt.axis("equal")
    plt.legend()
    plt.grid(True)
    plt.savefig(save_path + filename, dpi=300, bbox_inches="tight")
    plt.close()

def main():
    # In questo esercizio: ("rk4", "nonlinear") di default
    integrator = "rk4"
    model = "nonlinear"

    # Istanzia la simulation
    sim = Simulation(lf, lr, mass, Iz, dt, integrator=integrator, model=model)

    # Storage
    x_vals, y_vals, theta_vals = [], [], []
    vx_vals, vy_vals, r_vals = [], [], []
    alpha_f_vals, alpha_r_vals = [], []
    beta_vals, delta_vals = [], []
    lateral_error_vals, vel_error_vals = [], []
    ax_vals = []
    fyf_vals, fyr_vals = [], []

    for step in range(steps):
        # Calcolo del comando di accelerazione (PID)
        ax_cmd = long_control_pid.compute(target_speed, sim.vx, dt)
        steer_cmd = 0.0   # In exercise 1, sterzo = 0 (oppure potresti lasciarlo calcolato da pure pursuit)

        # Calcolo errori
        v_err = target_speed - sim.vx
        vel_error_vals.append(v_err)

        # Lateral error? Se steering = 0, sta su una retta, ma volendo calcolarlo:
        # Per coerenza usiamo la spline. 
        # (Puoi anche saltare se in Ex1 non serve.)
        actual_position = (sim.x, sim.y)
        path_spline.update_current_s(actual_position)
        prj = path_spline.calc_position(path_spline.cur_s)
        dx = prj[0] - sim.x
        dy = prj[1] - sim.y
        # Trasformazione in locale
        local_y = dx * math.sin(-sim.theta) + dy * math.cos(-sim.theta)
        lateral_error_vals.append(local_y)

        # Integra
        sim.integrate(ax_cmd, steer_cmd)

        # Salva
        x_vals.append(sim.x)
        y_vals.append(sim.y)
        theta_vals.append(sim.theta)
        vx_vals.append(sim.vx)
        vy_vals.append(sim.vy)
        r_vals.append(sim.r)

        # Slip angles
        alpha_f = steer_cmd - np.arctan((sim.vy + sim.l_f*sim.r)/max(0.5, sim.vx))
        alpha_r = -(np.arctan((sim.vy - sim.l_r*sim.r)/max(0.5, sim.vx)))
        alpha_f_vals.append(alpha_f)
        alpha_r_vals.append(alpha_r)

        # Forze
        Fz_f_nominal = (sim.l_r/(sim.l_f+sim.l_r))*sim.mass*9.81
        Fz_r_nominal = (sim.l_f/(sim.l_f+sim.l_r))*sim.mass*9.81
        phi_f = (1 - sim.E_f)*(sim.B_f*alpha_f) + sim.E_f*np.arctan(sim.B_f*alpha_f)
        fyf = Fz_f_nominal * sim.D_f * np.sin(sim.C_f*np.arctan(phi_f))
        phi_r = (1 - sim.E_r)*(sim.B_r*alpha_r) + sim.E_r*np.arctan(sim.B_r*alpha_r)
        fyr = Fz_r_nominal * sim.D_r * np.sin(sim.C_r*np.arctan(phi_r))
        fyf_vals.append(fyf)
        fyr_vals.append(fyr)

        # Beta e delta
        beta = np.arctan(sim.vy / max(0.1, sim.vx))
        beta_vals.append(beta)
        delta_vals.append(steer_cmd)

        # ax calcolato
        ax_vals.append(ax_cmd)

    # Definisci labels
    labels = [f"{integrator.capitalize()} - {model.capitalize()}"]

    # Ora i plot
    # 1) Traiettoria x,y (anche se in Ex1 lo sterzo e' 0, 
    #    se la path e' dritta vediamo un grafico.)
    plot_trajectory([x_vals], [y_vals], labels, path_spline, filename="trajectory.png")

    # 2) vx
    plot_and_save([range(len(vx_vals))],
                  [vx_vals],
                  [f"{integrator.capitalize()} - {model.capitalize()}"],
                  "Longitudinal Velocity",
                  "Time Step",
                  "Velocity (m/s)",
                  "vx.png")

    # 3) vy
    plot_and_save([range(len(vy_vals))],
                  [vy_vals],
                  [f"{integrator.capitalize()} - {model.capitalize()}"],
                  "Lateral Velocity",
                  "Time Step",
                  "Velocity (m/s)",
                  "vy.png")

    # 4) front slip angle
    plot_and_save([range(len(alpha_f_vals))],
                  [alpha_f_vals],
                  [f"{integrator.capitalize()} - {model.capitalize()}"],
                  "Front Slip Angle",
                  "Time Step",
                  "Slip Angle (rad)",
                  "alpha_f.png")

    # 5) rear slip angle
    plot_and_save([range(len(alpha_r_vals))],
                  [alpha_r_vals],
                  [f"{integrator.capitalize()} - {model.capitalize()}"],
                  "Rear Slip Angle",
                  "Time Step",
                  "Slip Angle (rad)",
                  "alpha_r.png")

    # 6) steering angle
    plot_and_save([range(len(delta_vals))],
                  [delta_vals],
                  [f"{integrator.capitalize()} - {model.capitalize()}"],
                  "Steering Angle",
                  "Time Step",
                  "Steering (rad)",
                  "steer.png")

    # 7) side slip angle
    plot_and_save([range(len(beta_vals))],
                  [beta_vals],
                  [f"{integrator.capitalize()} - {model.capitalize()}"],
                  "Side Slip Angle",
                  "Time Step",
                  "Angle (rad)",
                  "beta.png")

    # 8) Lateral error
    plot_and_save([range(len(lateral_error_vals))],
                  [lateral_error_vals],
                  [f"{integrator.capitalize()} - {model.capitalize()}"],
                  "Lateral Error",
                  "Time Step",
                  "Error (m)",
                  "lateral_error.png")

    # 9) velocity error
    plot_and_save([range(len(vel_error_vals))],
                  [vel_error_vals],
                  [f"{integrator.capitalize()} - {model.capitalize()}"],
                  "Velocity Error",
                  "Time Step",
                  "Error (m/s)",
                  "velocity_error.png")

    # 10) ax
    plot_and_save([range(len(ax_vals))],
                  [ax_vals],
                  [f"{integrator.capitalize()} - {model.capitalize()}"],
                  "Longitudinal Acceleration (cmd)",
                  "Time Step",
                  "Acceleration (m/s^2)",
                  "ax_cmd.png")

    # 11) Forze vs slip angles
    # front
    plot_and_save([alpha_f_vals],
                  [fyf_vals],
                  [f"{integrator.capitalize()} - {model.capitalize()}"],
                  "Front Lateral Force vs Front Slip Angle",
                  "Alpha_f (rad)",
                  "Force (N)",
                  "fyf_vs_af.png")

    # rear
    plot_and_save([alpha_r_vals],
                  [fyr_vals],
                  [f"{integrator.capitalize()} - {model.capitalize()}"],
                  "Rear Lateral Force vs Rear Slip Angle",
                  "Alpha_r (rad)",
                  "Force (N)",
                  "fyr_vs_ar.png")

if __name__ == "__main__":
    main()