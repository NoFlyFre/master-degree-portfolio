import os
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation
import matplotlib
from simulation import Simulation
import pid
import purepursuit
import stanley
from mpc import *
import cubic_spline_planner
import math
import frenet_optimal_trajectory as fp 

matplotlib.use('TkAgg')  # oppure 'Agg', 'Qt5Agg', etc.

# --------------------------------------------------------------------------------
# Parametri dell'esercizio (modificabili):
exercise_id = 1
sim_speed   = 15.0   # (o 15.0, 20.0, 25.0, ...)

# Creazione della cartella di output per i plot e i dati
out_folder = f"results/Exercise_{exercise_id}_{int(sim_speed)}_ms_pp"
os.makedirs(out_folder, exist_ok=True)
# --------------------------------------------------------------------------------

# Simulation parameters
dt         = 0.05   # Time step (s)
ax         = 0.0    # fallback costante
steer      = 0.0    # fallback costante
sim_time   = 113.36    # Impostare tempo di simulazione in secondi
                    # Per esercizio 1.1: 165.96 secondi
                    # Per esercizio 1.2: 113.36 secondi
                    # Per esercizio 2.1: 87.528 secondi
                    # Per esercizio 2.2: 72.55 secondi
steps      = int(sim_time / dt)

# Controllo:
target_speed = sim_speed

# Vehicle parameters
lf         = 1.156
lr         = 1.42
wheelbase  = lf + lr
mass       = 1200
Iz         = 1792
max_steer  = 3.14

# PID Longitudinale
long_control_pid = pid.PIDController(kp=1.5, ki=0.6, kd=0.06, output_limits=(-2, 2))

# Lateral Control
k_pp         = 0.2
look_ahead   = 4.0
k_stanley    = 0.001
pp_controller = purepursuit.PurePursuitController(wheelbase, max_steer)
stanley_controller = stanley.StanleyController(k_stanley, lf, max_steer)

# Obstacles
ob = np.array([
    [100.0,  -0.5],
    [400.0,   0.5],
    [570.0,  29.0],
    [600.0, 100.0],
    [120.0, 200.0],
    [33.0,  200.0],
    [-70.0, 171.0],
    [-100.0,100.0]
])

def load_path(file_path):
    file = open(file_path, "r")
    xs, ys = [], []
    while(file.readline()):
        line = file.readline()
        xs.append(float(line.split(",")[0]))
        ys.append(float(line.split(",")[1]))
    return xs, ys

# Carichiamo la traiettoria
xs, ys = load_path("oval_trj.txt")
path_spline = cubic_spline_planner.Spline2D(xs, ys)

def point_transform(trg, pose, yaw):
    return [trg[0] - pose[0], trg[1] - pose[1]]

def run_simulation(ax_fallback, steer_fallback, dt, integrator, model, steps=500):

    sim = Simulation(lf, lr, mass, Iz, dt, integrator=integrator, model=model)

    # Array per i risultati
    x_vals, y_vals, theta_vals = [], [], []
    vx_vals, vy_vals, r_vals   = [], [], []
    alpha_f_vals, alpha_r_vals = [], []
    frenet_x, frenet_y         = [], []

    # Aggiunte: steering, side slip, lateral err, speed err, ax PID
    delta_vals          = []
    beta_vals           = []
    lateral_err_vals    = []
    frenet_lat_err_vals = []
    speed_err_vals      = []
    ax_pid_vals         = []

    casadi_model()

    # Frenet states
    c_speed = 0.0
    c_accel = 0.0
    c_d     = 0.0
    c_d_d   = 0.0
    c_d_dd  = 0.0
    s0      = 0.0

    for step_i in range(steps):

        t_now = step_i * dt
        print(f"Time: {t_now:.2f}")

        # 1) PID per la velocità
        ax_pid = long_control_pid.compute(target_speed, sim.vx, dt)
        ax_pid_vals.append(ax_pid)

        # 2) Frenet Planner
        frenet_path = fp.frenet_optimal_planning(
            path_spline, s0, c_speed, c_accel, c_d, c_d_d, c_d_dd, ob
        )

        if frenet_path is None:
            print("Nessuna path Frenet disponibile, break.")
            break

        frenetpath_spline = cubic_spline_planner.Spline2D(frenet_path.x, frenet_path.y)

        # Update posizione
        actual_position = (sim.x, sim.y)
        path_spline.update_current_s(actual_position)
        frenetpath_spline.update_current_s(actual_position)
        
        # Lateral error globale
        global_position_projected = path_spline.calc_position(path_spline.cur_s)
        prj = [global_position_projected[0], global_position_projected[1]]
        local_error = point_transform(prj, actual_position, sim.theta)
        lateral_err_vals.append(local_error[1])
        
        # Calcolo errore laterale Frenet
        frenet_global_position_projected = frenetpath_spline.calc_position(frenetpath_spline.cur_s)
        frenet_prj = [frenet_global_position_projected[0], frenet_global_position_projected[1]]
        frenetlocal_error = point_transform(frenet_prj, actual_position, sim.theta)
        frenet_lat_err_vals.append(frenetlocal_error[1])

        # Fermo se eccessivo
        if abs(local_error[1]) > 4.0:
            print("Errore laterale > 4, stop simulazione.")
            break

        # Trova nearest idx
        nearest_idx = 0
        nearest_distance = abs(path_spline.cur_s - frenet_path.s[0])
        for i in range(len(frenet_path.s)):
            dist = abs(path_spline.cur_s - frenet_path.s[i])
            if dist < nearest_distance:
                nearest_idx = i
                nearest_distance = dist

        s0      = frenet_path.s[nearest_idx]
        c_d     = frenet_path.d[nearest_idx]
        c_d_d   = frenet_path.d_d[nearest_idx]
        c_d_dd  = frenet_path.d_dd[nearest_idx]
        c_speed = frenet_path.s_d[nearest_idx]
        c_accel = frenet_path.s_dd[nearest_idx]

        # Speed error
        speed_err = target_speed - sim.vx
        speed_err_vals.append(speed_err)

        # 3) Lateral Control (PurePursuit)
        Lf = k_pp * sim.vx + look_ahead
        s_pos = frenetpath_spline.cur_s + Lf
        trg_global = frenetpath_spline.calc_position(s_pos)

        # rear axle
        pp_position = (
            sim.x + lr * math.cos(sim.theta),
            sim.y + lr * math.sin(sim.theta)
        )
        loc_trg = point_transform(trg_global, pp_position, sim.theta)

        steer_cmd = pp_controller.compute_steering_angle(loc_trg, sim.theta, Lf)
        delta_vals.append(steer_cmd)
        
        print("X: ",sim.x , "\nY:", sim.y)

        # 4) integrazione
        sim.integrate(ax_pid, float(steer_cmd))

        x_vals.append(sim.x)
        y_vals.append(sim.y)
        theta_vals.append(sim.theta)
        vx_vals.append(sim.vx)
        vy_vals.append(sim.vy)
        r_vals.append(sim.r)

        # Slip angles
        alpha_f = steer_cmd - math.atan2(sim.vy + sim.l_f * sim.r, max(sim.vx, 0.5))
        alpha_r = - math.atan2(sim.vy - sim.l_r * sim.r, max(sim.vx, 0.5))
        alpha_f_vals.append(alpha_f)
        alpha_r_vals.append(alpha_r)

        # side slip angle beta
        if abs(sim.vx) > 0.1:
            beta = math.atan2(sim.vy, sim.vx)
        else:
            beta = 0.0
        beta_vals.append(beta)

        frenet_x.append(frenet_path.x[0])
        frenet_y.append(frenet_path.y[0])

    return (
        x_vals, y_vals, theta_vals,
        vx_vals, vy_vals, r_vals,
        alpha_f_vals, alpha_r_vals,
        delta_vals, beta_vals,
        lateral_err_vals, frenet_lat_err_vals, speed_err_vals,
        ax_pid_vals,
        frenet_x, frenet_y
    )

def main():
    configs = [("rk4", "nonlinear")]

    all_results = []
    labels = []

    for integrator, model in configs:
        sim_data = run_simulation(ax, steer, dt, integrator, model, steps)
        all_results.append(sim_data)
        labels.append(f"{integrator.capitalize()} - {model.capitalize()}")

    # Estraggo i dati
    x_results, y_results          = [], []
    theta_results, vx_results     = [], []
    vy_results, r_results         = [], []
    alpha_f_results, alpha_r_results = [], []
    delta_results, beta_results   = [], []
    lat_err_results, spd_err_results, frenet_lat_err_results = [], [], []
    ax_vals_results               = []
    frenet_x_results, frenet_y_results = [], []
    
    for sim_data in all_results:
        (xv, yv, thv, vxv, vyv, rv,
        afv, arv, deltav, betav,
        global_laterr, frenet_laterr, spderr,
        axv, fx, fy) = sim_data

        x_results.append(xv)
        y_results.append(yv)
        theta_results.append(thv)
        vx_results.append(vxv)
        vy_results.append(vyv)
        r_results.append(rv)
        alpha_f_results.append(afv)
        alpha_r_results.append(arv)
        delta_results.append(deltav)
        beta_results.append(betav)
        lat_err_results.append(global_laterr)
        frenet_lat_err_results.append(frenet_laterr)
        spd_err_results.append(spderr)
        ax_vals_results.append(axv)
        frenet_x_results.append(fx)
        frenet_y_results.append(fy)

    # --------------------- SALVATAGGIO PLOT --------------------------
    # 1) Traiettoria 2D
    plt.figure(figsize=(10,6))
    for i, lab in enumerate(labels):
        plt.plot(x_results[i], y_results[i], label=lab)
    # Frenet path
    for i in range(len(frenet_x_results)):
        plt.plot(frenet_x_results[i], frenet_y_results[i], '--', color='green')
    # Spline
    spline_x = [path_spline.calc_position(s)[0] for s in np.linspace(0, path_spline.s[-1], 1000)]
    spline_y = [path_spline.calc_position(s)[1] for s in np.linspace(0, path_spline.s[-1], 1000)]
    plt.plot(spline_x, spline_y, '--r', label="Path Spline")
    # Obstacles
    if len(ob[0]) != 0:
        plt.scatter(ob[:,0], ob[:,1], c='black', marker='x', label='Obstacles')
    plt.title("2D Trajectory")
    plt.xlabel("X (m)")
    plt.ylabel("Y (m)")
    plt.grid(True)
    plt.axis('equal')
    plt.legend()
    plt.savefig(os.path.join(out_folder, "Trajectory2D.png"), dpi=150)
    plt.close()

    # 2) Heading
    plt.figure()
    for i, lab in enumerate(labels):
        plt.plot(theta_results[i], label=lab)
    plt.title("Heading Angle")
    plt.xlabel("Time Step")
    plt.ylabel("Theta (rad)")
    plt.grid(True)
    plt.legend()
    plt.savefig(os.path.join(out_folder, "HeadingAngle.png"), dpi=150)
    plt.close()

    # 3) vx
    plt.figure()
    for i, lab in enumerate(labels):
        plt.plot(vx_results[i], label=lab)
    plt.title("Longitudinal Velocity")
    plt.xlabel("Time Step")
    plt.ylabel("vx (m/s)")
    plt.grid(True)
    plt.legend()
    plt.savefig(os.path.join(out_folder, "LongitudinalVelocity.png"), dpi=150)
    plt.close()

    # 4) vy
    plt.figure()
    for i, lab in enumerate(labels):
        plt.plot(vy_results[i], label=lab)
    plt.title("Lateral Velocity")
    plt.xlabel("Time Step")
    plt.ylabel("vy (m/s)")
    plt.grid(True)
    plt.legend()
    plt.savefig(os.path.join(out_folder, "LateralVelocity.png"), dpi=150)
    plt.close()

    # 5) Yaw Rate
    plt.figure()
    for i, lab in enumerate(labels):
        plt.plot(r_results[i], label=lab)
    plt.title("Yaw Rate (r)")
    plt.xlabel("Time Step")
    plt.ylabel("r (rad/s)")
    plt.grid(True)
    plt.legend()
    plt.savefig(os.path.join(out_folder, "YawRate.png"), dpi=150)
    plt.close()

    # 6) Slip angles
    plt.figure()
    for i, lab in enumerate(labels):
        plt.plot(alpha_f_results[i], label=f"Front Slip {lab}")
        plt.plot(alpha_r_results[i], label=f"Rear Slip {lab}")
    plt.title("Slip Angles")
    plt.xlabel("Time Step")
    plt.ylabel("alpha (rad)")
    plt.grid(True)
    plt.legend()
    plt.savefig(os.path.join(out_folder, "SlipAngles.png"), dpi=150)
    plt.close()

    # 7) Steering
    plt.figure()
    for i, lab in enumerate(labels):
        plt.plot(delta_results[i], label=f"delta {lab}")
    plt.title("Steering Angle")
    plt.xlabel("Time Step")
    plt.ylabel("delta (rad)")
    plt.grid(True)
    plt.legend()
    plt.savefig(os.path.join(out_folder, "SteeringAngle.png"), dpi=150)
    plt.close()

    # 8) Side Slip Angle
    plt.figure()
    for i, lab in enumerate(labels):
        plt.plot(beta_results[i], label=f"beta {lab}")
    plt.title("Side Slip Angle (beta)")
    plt.xlabel("Time Step")
    plt.ylabel("beta (rad)")
    plt.grid(True)
    plt.legend()
    plt.savefig(os.path.join(out_folder, "SideSlipAngle.png"), dpi=300)
    plt.close()

    # ----------------------------------------------------------------------
    # 9a) Lateral Error (globale)
    plt.figure(figsize=(10, 6))
    for i, lab in enumerate(labels):
        plt.plot(lat_err_results[i], label=f"Global LateralErr {lab}")
    plt.title("Global Lateral Error")
    plt.xlabel("Time Step")
    plt.ylabel("Error [m]")
    plt.grid(True)
    plt.legend()
    plt.savefig(os.path.join(out_folder, "Global_LateralError.png"), dpi=300)  # dpi più alto
    plt.close()

    # 9b) Lateral Error (Frenet)
    plt.figure(figsize=(10, 6))
    for i, lab in enumerate(labels):
        plt.plot(frenet_lat_err_results[i], label=f"Frenet LateralErr {lab}")
    plt.title("Frenet Lateral Error")
    plt.xlabel("Time Step")
    plt.ylabel("Error [m]")
    plt.grid(True)
    plt.legend()
    plt.savefig(os.path.join(out_folder, "Frenet_LateralError.png"), dpi=300)
    plt.close()

    # 9c) Speed Error
    plt.figure(figsize=(10, 6))
    for i, lab in enumerate(labels):
        plt.plot(spd_err_results[i], label=f"SpeedErr {lab}")
    plt.title("Speed Error")
    plt.xlabel("Time Step")
    plt.ylabel("Error [m/s]")
    plt.grid(True)
    plt.legend()
    plt.savefig(os.path.join(out_folder, "SpeedError.png"), dpi=300)
    plt.close()
    # ----------------------------------------------------------------------

    # 10) Longitudinal Acceleration
    plt.figure()
    for i, lab in enumerate(labels):
        plt.plot(ax_vals_results[i], label=f"ax {lab}")
    plt.title("Longitudinal acceleration (PID)")
    plt.xlabel("Time Step")
    plt.ylabel("ax [m/s^2]")
    plt.grid(True)
    plt.legend()
    plt.savefig(os.path.join(out_folder, "LongitudinalAcceleration.png"), dpi=300)
    plt.close()

    print("Fine. Grafici salvati in:", out_folder)

if __name__ == "__main__":
    main()