import os
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation
from simulation import Simulation

def get_simulation_parameters(exercise_number):
    if exercise_number == 1:
        dt = 0.001
        sim_time = 5.0
        ax = 1.0
        velocities = [10.0, 27.0]  # due velocità
        steering_angles = None  # Qui, se sinusoidale, potresti non definire angoli costanti
        configs = [
            ("rk4", "kinematic"),
            ("rk4", "linear"),
            ("rk4", "nonlinear"),
            ("euler", "kinematic"),
            ("euler", "linear"),
            ("euler", "nonlinear")
        ]
        use_sinusoidal_steer = True  # Esercizio 1 sterzo sinusoidale

    elif exercise_number == 2:
        dt = 0.001
        sim_time = 5.0
        ax = 1.0
        velocities = [24.0]
        steering_angles = [0.01, 0.055]
        configs = [
            ("rk4", "kinematic"),
            ("rk4", "linear"),
            ("rk4", "nonlinear"),
            ("euler", "kinematic"),
            ("euler", "linear"),
            ("euler", "nonlinear")
        ]
        use_sinusoidal_steer = False

    elif exercise_number == 3:
        # Stesse condizioni dell'esercizio 2, ma con dt più grande e sim_time più lungo
        dt = 0.04
        sim_time = 10.0
        ax = 1.0
        velocities = [24.0]
        steering_angles = [0.01, 0.055]  # Stessi angoli dell'esercizio 2
        configs = [
            ("rk4", "kinematic"),
            ("rk4", "linear"),
            ("rk4", "nonlinear"),
            ("euler", "kinematic"),
            ("euler", "linear"),
            ("euler", "nonlinear")
        ]
        use_sinusoidal_steer = False  # Come esercizio 2

    steps = int(sim_time / dt)
    return dt, sim_time, steps, ax, velocities, steering_angles, configs, use_sinusoidal_steer

def build_save_dir(base_dir, exercise_number, initial_velocity=None, steer=None, integrator=None, model=None):
    """
    Costruisce il percorso della directory dove salvare i risultati in base
    a vari parametri. Puoi adattare questa funzione alle tue esigenze.
    """
    # Partiamo dalla cartella base e aggiungiamo l'esercizio
    path = os.path.join(base_dir, f"Exercise{exercise_number}")
    
    # Se hai una o più velocità, aggiungile
    if initial_velocity is not None:
        path = os.path.join(path, f"Vx{int(initial_velocity)}")
    
    # Aggiungi l'angolo di sterzo (sinusoidale o costante)
    if steer is not None:
        # Se è sinusoidale puoi chiamarla "SteerSin"
        # Se è costante, puoi mettere ad esempio "Steer0.01"
        # Qui assumiamo che il valore di steer sia numerico
        path = os.path.join(path, f"Steer{steer}")
    
    # Aggiungi integratore e modello
    # Potresti voler creare sottocartelle per integratore e modello separatamente
    # oppure concatenare le informazioni nel filename.
    # Qui creiamo una struttura: integratore/model/
    if integrator is not None:
        path = os.path.join(path, integrator)
    if model is not None:
        path = os.path.join(path, model)
    
    # Ora path è qualcosa come:
    # results/Exercise1/Vx10/Steer0.1/rk4/linear
    return path

def plot_comparison(results, labels, title, xlabel, ylabel, save_dir=None, filename=None):
    """ Plot comparison of results for a specific state variable. """
    plt.figure(figsize=(10, 6))
    for i, result in enumerate(results):
        plt.plot(result, label=labels[i])
    plt.title(title)
    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    plt.legend()
    plt.grid(True)
    
    if save_dir is not None and filename is not None:
        os.makedirs(save_dir, exist_ok=True)  # Crea la cartella se non esiste
        plt.savefig(os.path.join(save_dir, filename))
        plt.close()
    else:
        plt.show()  # Se non indichi dove salvare, mostra a schermo

def plot_trajectory(x_vals, y_vals, labels, save_dir=None, filename=None):
    """ Plot 2D trajectory (x vs y) for all simulation configurations. """
    plt.figure(figsize=(10, 6))
    for i in range(len(x_vals)):
        plt.plot(x_vals[i], y_vals[i], label=labels[i])
    plt.title("2D Trajectory Comparison")
    plt.xlabel("X Position (m)")
    plt.ylabel("Y Position (m)")
    plt.legend()
    plt.grid(True)
    plt.axis("equal")
    
    if save_dir is not None and filename is not None:
        os.makedirs(save_dir, exist_ok=True)
        plt.savefig(os.path.join(save_dir, filename))
        plt.close()
    else:
        plt.show()

def run_simulation(ax, steer, dt, integrator, model, steps, initial_velocity, use_sinusoidal_steer=False):
    """ Run a simulation with the given parameters and return all states. """
    # Vehicle parameters
    lf = 1.156          # Distance from COG to front axle (m)
    lr = 1.42           # Distance from COG to rear axle (m)
    mass = 1200         # Vehicle mass (kg)
    Iz = 1792           # Yaw moment of inertia (kg*m^2)

    # Initialize the simulation
    sim = Simulation(lf, lr, mass, Iz, dt, integrator=integrator, model=model)
    sim.vx = initial_velocity  # Set initial longitudinal velocity

    # Storage for state variables and slip angles
    x_vals, y_vals, theta_vals, vx_vals, vy_vals, r_vals = [], [], [], [], [], []
    alpha_f_vals, alpha_r_vals = [], []  # Slip angles
    beta_vals = []
    delta_vals = []
    fyf_vals = []
    fyr_vals = []

    # Sterzo sinusoidale
    steer_max = 0.1
    frequency = 0.5

    for step in range(steps):
        # Calcola il valore dello sterzo
        if use_sinusoidal_steer:
            time = step * dt
            steer = steer_max * np.sin(2 * np.pi * frequency * time)  # Sinusoidal steering angle

        # Esegui un passo di integrazione
        sim.integrate(ax, steer)

        # Salva i risultati
        x_vals.append(sim.x)
        y_vals.append(sim.y)
        theta_vals.append(sim.theta)
        vx_vals.append(sim.vx)
        vy_vals.append(sim.vy)
        r_vals.append(sim.r)

        # Angoli di deriva
        alpha_f = steer - np.atan((sim.vy + sim.l_f * sim.r) / sim.vx)
        alpha_r = -np.atan((sim.vy - sim.l_r * sim.r) / sim.vx)

        alpha_f_vals.append(alpha_f)
        alpha_r_vals.append(alpha_r)

        # Forze laterali
        Fz_f_nominal = (sim.l_r / (sim.l_f + sim.l_r)) * sim.mass * 9.81
        Fz_r_nominal = (sim.l_f / (sim.l_f + sim.l_r)) * sim.mass * 9.81
        if sim.model == "linear":
            Fyf = sim.Cf * alpha_f * Fz_f_nominal
            Fyr = sim.Cr * alpha_r * Fz_r_nominal
        elif sim.model == "nonlinear":
            phi_f = (1 - sim.E_f) * (sim.B_f * alpha_f) + sim.E_f * np.arctan(sim.B_f * alpha_f)
            Fyf = Fz_f_nominal * sim.D_f * np.sin(sim.C_f * np.arctan(phi_f))
            phi_r = (1 - sim.E_r) * (sim.B_r * alpha_r) + sim.E_r * np.arctan(sim.B_r * alpha_r)
            Fyr = Fz_r_nominal * sim.D_r * np.sin(sim.C_r * np.arctan(phi_r))
        else:
            Fyf, Fyr = 0, 0

        fyf_vals.append(Fyf)
        fyr_vals.append(Fyr)

        beta = np.arctan(sim.vy / sim.vx)
        beta_vals.append(beta)
        delta_vals.append(steer)

    return x_vals, y_vals, theta_vals, vx_vals, vy_vals, r_vals, alpha_f_vals, alpha_r_vals, beta_vals, delta_vals, fyf_vals, fyr_vals

def plot_force_vs_slip_angle(alpha_results, force_results, labels, title, save_dir=None, filename=None, xlabel="Slip Angle (rad)", ylabel="Lateral Force (N)"):
    """ Plot lateral force vs. slip angle for all configurations. """
    plt.figure(figsize=(10, 6))
    for i in range(len(alpha_results)):
        plt.plot(alpha_results[i], force_results[i], label=labels[i])
    plt.title(title)
    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    plt.legend()
    plt.grid(True)
    
    if save_dir is not None and filename is not None:
        os.makedirs(save_dir, exist_ok=True)
        plt.savefig(os.path.join(save_dir, filename))
        plt.close()
    else:
        plt.show()

def main():
    # Itera su tutti gli esercizi
    for exercise_number in [1, 2, 3]:
        print(f"Running Exercise {exercise_number}...")

        # Ottieni i parametri per l'esercizio corrente
        dt, sim_time, steps, ax, velocities, steering_angles, configs, use_sinusoidal_steer = get_simulation_parameters(exercise_number)
        base_dir = "results"

        # Se lo sterzo è sinusoidale, setta il valore corretto
        if steering_angles is None:
            steering_angles = [None]  # Per gestire il caso dello sterzo sinusoidale

        # Itera per tutte le combinazioni di velocità e angolo di sterzo
        for initial_velocity in velocities:
            for steer in steering_angles:
                all_results = []
                labels = []
                for integrator, model in configs:
                    # Esegui la simulazione
                    results = run_simulation(ax, steer, dt, integrator, model, steps, initial_velocity, use_sinusoidal_steer=use_sinusoidal_steer)
                    all_results.append(results)

                    # Costruisci l'etichetta per i grafici
                    lbl = f"{integrator.capitalize()} - {model.capitalize()} - Vx={initial_velocity} m/s"
                    lbl += f" - Steer={steer}" if steer is not None else " - SteerSin"
                    labels.append(lbl)

                # Estrai i dati per i grafici
                x_results = [result[0] for result in all_results]
                y_results = [result[1] for result in all_results]
                theta_results = [result[2] for result in all_results]
                vx_results = [result[3] for result in all_results]
                vy_results = [result[4] for result in all_results]
                r_results = [result[5] for result in all_results]
                alpha_f_results = [result[6] for result in all_results]
                alpha_r_results = [result[7] for result in all_results]
                beta_results = [result[8] for result in all_results]
                delta_results = [result[9] for result in all_results]
                fyf_results = [result[10] for result in all_results]
                fyr_results = [result[11] for result in all_results]

                # Costruisci la directory di salvataggio
                save_base = build_save_dir(base_dir, exercise_number, initial_velocity=initial_velocity, steer="Sin" if steer is None else steer)

                # Salva i grafici
                traj_dir = os.path.join(save_base, "")
                plot_trajectory(x_results, y_results, labels, save_dir=traj_dir, filename="trajectory_comparison.png")

                heading_dir = os.path.join(save_base, "")
                plot_comparison(theta_results, labels, "Heading Angle Comparison", "Time Step", "Heading Angle (rad)",
                                save_dir=heading_dir, filename="heading_angle_comparison.png")

                vx_dir = os.path.join(save_base, "")
                plot_comparison(vx_results, labels, "Longitudinal Velocity Comparison", "Time Step", "Velocity (m/s)",
                                save_dir=vx_dir, filename="vx_comparison.png")

                vy_dir = os.path.join(save_base, "")
                plot_comparison(vy_results, labels, "Lateral Velocity Comparison", "Time Step", "Lateral Velocity (m/s)",
                                save_dir=vy_dir, filename="vy_comparison.png")

                r_dir = os.path.join(save_base, "")
                plot_comparison(r_results, labels, "Yaw Rate Comparison", "Time Step", "Yaw Rate (rad/s)",
                                save_dir=r_dir, filename="r_comparison.png")

                alphaf_dir = os.path.join(save_base, "")
                plot_comparison(alpha_f_results, labels, "Front Slip Angle Comparison", "Time Step", "Slip Angle (rad) - Front",
                                save_dir=alphaf_dir, filename="alpha_f_comparison.png")

                alphar_dir = os.path.join(save_base, "")
                plot_comparison(alpha_r_results, labels, "Rear Slip Angle Comparison", "Time Step", "Slip Angle (rad) - Rear",
                                save_dir=alphar_dir, filename="alpha_r_comparison.png")

                delta_dir = os.path.join(save_base, "")
                plot_comparison(delta_results, labels, "Steering Angle Comparison", "Time Step", "Steering Angle (rad)",
                                save_dir=delta_dir, filename="delta_comparison.png")

                beta_dir = os.path.join(save_base, "")
                plot_comparison(beta_results, labels, "Side Slip Angle Comparison", "Time Step", "Side Slip Angle (rad)",
                                save_dir=beta_dir, filename="beta_comparison.png")

                fyf_dir = os.path.join(save_base, "")
                plot_comparison(fyf_results, labels, "Front Lateral Forces", "Time Step", "Lateral Force (N)",
                                save_dir=fyf_dir, filename="fyf_comparison.png")

                fyr_dir = os.path.join(save_base, "")
                plot_comparison(fyr_results, labels, "Rear Lateral Forces", "Time Step", "Lateral Force (N)",
                                save_dir=fyr_dir, filename="fyr_comparison.png")

                # Salva i grafici delle forze in funzione degli angoli di deriva
                alpha_f_dir = os.path.join(save_base, "")
                plot_force_vs_slip_angle(alpha_f_results, fyf_results, labels, 
                                        "Front Lateral Forces vs. Front Slip Angles",
                                        save_dir=alpha_f_dir, filename="front_force_vs_slip_angle.png")

                alpha_r_dir = os.path.join(save_base, "")
                plot_force_vs_slip_angle(alpha_r_results, fyr_results, labels, 
                                        "Rear Lateral Forces vs. Rear Slip Angles",
                                        save_dir=alpha_r_dir, filename="rear_force_vs_slip_angle.png")

if __name__ == "__main__":
    main()