import numpy as np
import matplotlib.pyplot as plt

# Simulation class
class VehicleSimulation:
    def __init__(self, wheelbase, mass, dt):
        # Static parameters of the vehicle
        self.l_wb = wheelbase    # Wheelbase (m)
        self.mass = mass         # Mass of the vehicle (kg)
        self.dt = dt             # Time step for simulation (s)

        # Initial states
        self.x = 0               # x position (m)
        self.y = 0               # y position (m)
        self.theta = 0           # Heading (rad)
        self.v = 0               # Velocity (m/s)
        
    def simstep(self, ax, delta):
        # Update the state using Euler integration
        # ax: Longitudinal acceleration (m/s^2)
        # delta: Steering angle (rad)

        # Compute the kinematic equations of motion
        self.x += self.v * np.cos(self.theta) * self.dt  # Update x position
        self.y += self.v * np.sin(self.theta) * self.dt  # Update y position
        self.theta += (self.v / self.l_wb) * np.tan(delta) * self.dt  # Update heading
        self.v += ax * self.dt                         # Update velocity

def plot_vehicle_trajectory(states):
    # Plot the vehicle trajectory
    x_vals, y_vals = zip(*states)
    plt.figure(figsize=(8, 6))
    plt.plot(x_vals, y_vals, label="Vehicle Path")
    plt.xlabel('X Position (m)')
    plt.ylabel('Y Position (m)')
    plt.title('Vehicle Trajectory (Kinematic Single-Track Model)')
    plt.legend()
    plt.grid(True)
    plt.show()

# Main simulation loop
def run_simulation():
    # Set static parameters
    wheelbase = 2.5      # Wheelbase (m)
    mass = 1500          # Mass of the vehicle (kg)
    dt = 0.04            # Time step (s)
    
    # Initialize simulation
    sim = VehicleSimulation(wheelbase, mass, dt)
    
    # Commands (constant values)
    ax = 1.0             # Longitudinal acceleration (m/s^2)
    steer = 0.1          # Steering angle (rad)
    
    states = []

    # Run the simulation for 500 steps (20 seconds)
    for _ in range(500):
        # Call the simulation step
        sim.simstep(ax, steer)
        
        # Store the current state (x, y)
        states.append((sim.x, sim.y))
    
    # Plot the resulting states
    plot_vehicle_trajectory(states)

# Run the simulation
if __name__ == "__main__":
    run_simulation()
