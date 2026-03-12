import numpy as np
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation
from simulation import Simulation
import pid
import purepursuit
# import stanley
# from mpc import *
import cubic_spline_planner
import math
import os

# Exercise name and directory
exercise_name = "Exercise_3_25_pp"
save_path = "Results/" + exercise_name + "/"
os.makedirs(save_path, exist_ok=True)

# Simulation parameters
dt = 0.001         # Time step (s)
ax = 0.0            # Constant longitudinal acceleration (m/s^2)
steer = 0.0      # Constant steering angle (rad)
sim_time = 90.0      # Simulation duration in seconds
steps = int(sim_time / dt)  # Simulation steps (30 seconds)

# Control references
target_speed = 25.0

# Vehicle parameters
lf = 1.156          # Distance from COG to front axle (m)
lr = 1.42           # Distance from COG to rear axle (m)
wheelbase = lf + lr
mass = 1200         # Vehicle mass (kg)
Iz = 1792           # Yaw moment of inertia (kg*m^2)
max_steer = 3.14  # Maximum steering angle in radians

# Create instance of PID for Longitudinal Control
long_control_pid = pid.PIDController(kp=1.5, ki=0.6, kd=0.06, output_limits=(-2, 2))

# Create instance of PurePursuit, Stanley and MPC for Lateral Control
k_pp = 0.001  # Speed proportional gain for Pure Pursuit
k_pp_curv = 0.1 # Curvature proportional gain for Pure Pursuit
look_ahead = 2.0  # Minimum look-ahead distance for Pure Pursuit
k_stanley = 0.001  # Gain for cross-track error for Stanley
pp_controller = purepursuit.PurePursuitController(wheelbase, max_steer)
# stanley_controller = stanley.StanleyController(k_stanley, lf, max_steer)

def load_path(file_path):
    file = open(file_path, "r")
    
    xs = []
    ys = []

    while(file.readline()):
        line = file.readline()
        xs.append( float(line.split(",")[0]) )
        ys.append( float(line.split(",")[1]) )
    return xs, ys

# Load path and create a spline
xs, ys = load_path("oval_trj.txt")
path_spline = cubic_spline_planner.Spline2D(xs, ys)

def point_transform(trg, pose, yaw):

    local_trg = [trg[0] - pose[0], trg[1] - pose[1]]

    return local_trg
# ===================== SEZIONE PLOTTING ==============================

def plot_and_save(xdata_list, ydata_list, labels, title, xlabel, ylabel, filename):
    """
    xdata_list e ydata_list sono liste di liste:
        - ad es. [ [x1_1, x1_2, ...], [x2_1, x2_2, ...], ... ]
    labels: etichette per le diverse curve
    filename: nome del file da salvare
    """
    plt.figure(figsize=(10, 6))
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

# ===================== FUNZIONE DI SIMULAZIONE =======================

def run_simulation(ax, steer, dt, integrator, model, steps=500):
    sim = Simulation(lf, lr, mass, Iz, dt, integrator=integrator, model=model)

    x_vals, y_vals, theta_vals = [], [], []
    vx_vals, vy_vals, r_vals = [], [], []
    alpha_f_vals, alpha_r_vals = [], []
    fyf_vals, fyr_vals = [], []
    beta_vals, delta_vals = [], []
    # Esempio di calcolo errori (se vuoi aggiungerli)
    lat_errors, vel_errors = [], []
    ax_list = []  # accelerazioni longitudinali (se vuoi salvare a_x, altrimenti rimuovi)

    for step in range(steps):
        # Long control
        ax_cmd = long_control_pid.compute(target_speed, sim.vx, dt)
        steer_cmd = 0.0

        # Aggiorna spline
        actual_position = (sim.x, sim.y)
        path_spline.update_current_s(actual_position)
        position_projected = path_spline.calc_position(path_spline.cur_s)

        # Lateral error
        local_error = point_transform(position_projected, actual_position, sim.theta)
        lat_err = local_error[1]
        lat_errors.append(lat_err)

        # Stop se errore troppo
        if abs(lat_err) > 1.0:
            print(f"Lateral error > 1.0 m, stopping at step={step}")
            break

        # PurePursuit
        Lf = k_pp * sim.vx + look_ahead
        s_pos = path_spline.cur_s + Lf
        curvature = path_spline.calc_curvature(s_pos)
        Lf = Lf + k_pp_curv * abs(curvature)
        trg_glob = path_spline.calc_position(s_pos)
        # Converte in locale
        pp_position = (sim.x + lr*math.cos(sim.theta), sim.y + lr*math.sin(sim.theta))
        loc_trg = point_transform(trg_glob, pp_position, sim.theta)
        steer_cmd = pp_controller.compute_steering_angle(loc_trg, sim.theta, Lf)

        # Integra
        sim.integrate(ax_cmd, steer_cmd)

        # Salva i dati
        x_vals.append(sim.x)
        y_vals.append(sim.y)
        theta_vals.append(sim.theta)
        vx_vals.append(sim.vx)
        vy_vals.append(sim.vy)
        r_vals.append(sim.r)
        delta_vals.append(steer_cmd)

        # Calcolo slip angles
        alpha_f = steer_cmd - np.arctan((sim.vy + sim.l_f*sim.r)/max(0.5, sim.vx))
        alpha_r = -(np.arctan((sim.vy - sim.l_r*sim.r)/max(0.5, sim.vx)))
        alpha_f_vals.append(alpha_f)
        alpha_r_vals.append(alpha_r)

        # Calcolo forze laterali
        Fz_f_nominal = (sim.l_r/(sim.l_f+sim.l_r))*sim.mass*9.81
        Fz_r_nominal = (sim.l_f/(sim.l_f+sim.l_r))*sim.mass*9.81
        phi_f = (1 - sim.E_f)*(sim.B_f*alpha_f) + sim.E_f*np.arctan(sim.B_f*alpha_f)
        fyf = Fz_f_nominal * sim.D_f * np.sin(sim.C_f*np.arctan(phi_f))
        phi_r = (1 - sim.E_r)*(sim.B_r*alpha_r) + sim.E_r*np.arctan(sim.B_r*alpha_r)
        fyr = Fz_r_nominal * sim.D_r * np.sin(sim.C_r*np.arctan(phi_r))
        fyf_vals.append(fyf)
        fyr_vals.append(fyr)

        beta = np.arctan(sim.vy/max(0.1, sim.vx))
        beta_vals.append(beta)

        # Velocity error in %:
        v_err = abs(target_speed - sim.vx)/target_speed*100
        vel_errors.append(v_err)

        # Calcolo a_x (accelerazione long):
        if step > 0:
            ax_calc = (sim.vx - vx_vals[-1])/dt
        else:
            ax_calc = 0.0
        ax_list.append(ax_calc)

    return (x_vals, y_vals, theta_vals, vx_vals, vy_vals, r_vals,
            alpha_f_vals, alpha_r_vals, fyf_vals, fyr_vals, beta_vals, delta_vals,
            lat_errors, vel_errors, ax_list)

# ===================== MAIN ========================

def main():
    configs = [
        ("rk4", "nonlinear"),
    ]
    all_results = []
    labels = []

    for integrator, model in configs:
        results = run_simulation(ax, steer, dt, integrator, model, steps)
        all_results.append(results)
        labels.append(f"{integrator.capitalize()} - {model.capitalize()}")

    (x_results, y_results, theta_results, vx_results, vy_results, r_results,
    alpha_f_results, alpha_r_results, fyf_results, fyr_results,
    beta_results, delta_results, lat_err_results, vel_err_results, ax_results
    ) = all_results[0]  # Abbiamo una sola configurazione

    # 1) Traiettoria
    plot_trajectory([x_results], [y_results], labels, path_spline, filename="trajectory.png")

    # 2) Longitudinal velocity
    plot_and_save([range(len(vx_results))],
                [vx_results],
                labels,
                "Longitudinal Velocity (vx)",
                "Time Step",
                "Velocity (m/s)",
                "longitudinal_velocity.png")

    # 3) Lateral velocity
    plot_and_save([range(len(vy_results))],
                [vy_results],
                labels,
                "Lateral Velocity (vy)",
                "Time Step",
                "Velocity (m/s)",
                "lateral_velocity.png")

    # 4) Front Slip Angle
    plot_and_save([range(len(alpha_f_results))],
                [alpha_f_results],
                labels,
                "Front Slip Angle (alpha_f)",
                "Time Step",
                "Slip Angle (rad)",
                "alpha_f.png")

    # 5) Rear Slip Angle
    plot_and_save([range(len(alpha_r_results))],
                [alpha_r_results],
                labels,
                "Rear Slip Angle (alpha_r)",
                "Time Step",
                "Slip Angle (rad)",
                "alpha_r.png")

    # 6) Steering angle
    plot_and_save([range(len(delta_results))],
                [delta_results],
                labels,
                "Steering Angle (delta)",
                "Time Step",
                "Angle (rad)",
                "steering_angle.png")

    # 7) Side Slip Angle (beta)
    plot_and_save([range(len(beta_results))],
                [beta_results],
                labels,
                "Side Slip Angle (beta)",
                "Time Step",
                "Angle (rad)",
                "side_slip_angle.png")

    # 8) Lateral Error
    plot_and_save([range(len(lat_err_results))],
                [lat_err_results],
                labels,
                "Lateral Error",
                "Time Step",
                "Error (m)",
                "lateral_error.png")

    # 9) Velocity Error
    plot_and_save([range(len(vel_err_results))],
                [vel_err_results],
                labels,
                "Velocity Error",
                "Time Step",
                "Error (%)",
                "velocity_error.png")

    # 10) Longitudinal Acceleration
    plot_and_save([range(len(ax_results))],
                [ax_results],
                labels,
                "Longitudinal Acceleration (approx)",
                "Time Step",
                "Acceleration (m/s^2)",
                "ax.png")

    # 11) Lateral tire force vs slip angle
    #    Esempio: Fyf vs alpha_f
    plot_and_save([alpha_f_results], [fyf_results],
                labels,
                "Front Lateral Force vs. Front Slip Angle",
                "Front Slip Angle (rad)",
                "Lateral Force (N)",
                "front_force_vs_slip.png")

    #    Esempio: Fyr vs alpha_r
    plot_and_save([alpha_r_results], [fyr_results],
                labels,
                "Rear Lateral Force vs. Rear Slip Angle",
                "Rear Slip Angle (rad)",
                "Lateral Force (N)",
                "rear_force_vs_slip.png")

    print("Plot salvati in:", save_path)

if __name__ == "__main__":
    main()