import numpy as np

class Simulation:
    def __init__(self, lf, lr, mass, Iz, dt, integrator="euler", model="kinematic"):
        """
        Initialize the simulation parameters.
        """
        self.l_f = lf                   # Distance to front axle (m)
        self.l_r = lr                   # Distance to rear axle (m)
        self.l_wb = lf + lr
        self.mass = mass                # Vehicle mass (kg)
        self.I_z = Iz                   # Yaw moment of inertia (kg*m^2)
        self.dt = dt                    # Time step (s)
        self.integrator = integrator    # Integrator choice
        self.model = model              # Model choice
        
        # Aerodynamic and rolling resistance parameters
        self.rho = 1.225               # Air density (kg/m^3)
        self.C_d = 0.3                 # Drag coefficient (typical for cars)
        self.A = 2.2                   # Frontal area (m^2)
        self.C_rr = 0.015              # Rolling resistance coefficient

        # Initialize states
        self.x = 0                      # X position (m)
        self.y = 0                      # Y position (m)
        self.theta = 0                  # Heading angle (rad)
        self.vx = 0.0                     # Longitudinal velocity (m/s)
        self.vy = 0                     # Lateral velocity (m/s)
        self.r = 0                      # Yaw rate (rad/s)

        # Pacejka's Magic Formula coefficients (provided in the assignment)
        self.B = 7.1433
        self.C = 1.3507
        self.D = 1.0489
        self.E = -0.0074722

        self.B_f = self.B
        self.C_f = self.C
        self.D_f = self.D
        self.E_f = self.E

        self.B_r = self.B
        self.C_r = self.C
        self.D_r = self.D
        self.E_r = self.E

        # Cornering stiffness front/rear (N/rad)
        self.Cf = self.B_f * self.C_f * self.D_f
        self.Cr = self.B_r * self.C_r * self.D_r
        
    def kinematic_model(self, ax, delta):
        """ Kinematic single-track model equations of motion. """

        # Aerodynamic drag and rolling resistance forces
        F_aero = 0.5 * self.rho * self.C_d * self.A * self.vx**2
        F_roll = self.C_rr * self.mass * 9.81

        # Equations of motion
        dx = np.array([
            self.vx * np.cos(self.theta),  # dx/dt
            self.vx * np.sin(self.theta),  # dy/dt
            (self.vx / self.l_wb) * np.tan(delta),  # dtheta/dt
            ax - (F_aero + F_roll) / self.mass,  # dvx/dt
            0,  # dvy/dt (assumed zero in kinematic model)
            0   # dr/dt (yaw rate is zero in kinematic model)
        ])
        return dx

    def linear_single_track_model(self, ax, delta):
        """ Linear single-track model with aerodynamic and rolling resistance. """

        # Tire slip angles
        alpha_f = delta - (self.vy + self.l_f * self.r) / self.vx
        alpha_r = - (self.vy - self.l_r * self.r) / self.vx

        # Vertical forces (nominal vertical load)
        Fz_f_nominal = (self.l_r / (self.l_f + self.l_r)) * self.mass * 9.81
        Fz_r_nominal = (self.l_f / (self.l_f + self.l_r)) * self.mass * 9.81

        # Front and rear lateral forces
        Fyf = self.Cf * alpha_f * Fz_f_nominal
        Fyr = self.Cr * alpha_r * Fz_r_nominal

        # Aerodynamic drag and rolling resistance forces
        F_aero = 0.5 * self.rho * self.C_d * self.A * self.vx**2
        F_roll = self.C_rr * self.mass * 9.81

        # Dynamics equations
        dx = np.array([
            self.vx * np.cos(self.theta) - self.vy * np.sin(self.theta),  # dx/dt
            self.vx * np.sin(self.theta) + self.vy * np.cos(self.theta),  # dy/dt
            self.r,  # dtheta/dt
            ax + self.vy * self.r - (F_aero + F_roll) / self.mass,  # dvx/dt
            (Fyf * np.cos(delta) + Fyr) / self.mass - self.vx * self.r,  # dvy/dt
            (self.l_f * Fyf * np.cos(delta) - self.l_r * Fyr) / self.I_z  # dr/dt
        ])
        return dx

    def nonlinear_single_track_model(self, ax, delta):
        """ Nonlinear single-track model with aerodynamic and rolling resistance. """

        # Evitare divisioni per zero
        if self.vx == 0:
            self.vx = 0.1

        # Angoli di deriva
        alpha_f = delta - (self.vy + self.l_f * self.r) / self.vx
        alpha_r = - (self.vy - self.l_r * self.r) / self.vx

        # Forze verticali (carico verticale nominale)
        Fz_f_nominal = (self.l_r / (self.l_f + self.l_r)) * self.mass * 9.81
        Fz_r_nominal = (self.l_f / (self.l_f + self.l_r)) * self.mass * 9.81

        # Forza laterale anteriore usando la formula di Pacejka
        phi_f = (1 - self.E_f) * (self.B_f * alpha_f) + self.E_f * np.arctan(self.B_f * alpha_f)
        Fyf = Fz_f_nominal * self.D_f * np.sin(self.C_f * np.arctan(phi_f))

        # Forza laterale posteriore usando la formula di Pacejka
        phi_r = (1 - self.E_r) * (self.B_r * alpha_r) + self.E_r * np.arctan(self.B_r * alpha_r)
        Fyr = Fz_r_nominal * self.D_r * np.sin(self.C_r * np.arctan(phi_r))

        # Forze aerodinamiche e di resistenza al rotolamento
        F_aero = 0.5 * self.rho * self.C_d * self.A * self.vx**2
        F_roll = self.C_rr * self.mass * 9.81

        # Equazioni dinamiche
        dx = np.array([
            self.vx * np.cos(self.theta) - self.vy * np.sin(self.theta),  # dx/dt
            self.vx * np.sin(self.theta) + self.vy * np.cos(self.theta),  # dy/dt
            self.r,  # dtheta/dt
            ax + self.vy * self.r - (F_aero + F_roll) / self.mass,  # dvx/dt
            (Fyf * np.cos(delta) + Fyr) / self.mass - self.vx * self.r,  # dvy/dt
            (self.l_f * Fyf * np.cos(delta) - self.l_r * Fyr) / self.I_z  # dr/dt
        ])
        return dx

    def integrate(self, ax, delta):
        """ Select the integrator method and apply it to update the state. """
        if self.integrator == "euler":
            self.euler_step(ax, delta)
        elif self.integrator == "rk4":
            self.rk4_step(ax, delta)

    def euler_step(self, ax, delta):
        """ Euler integration method. """
        dx = self.compute_dx(ax, delta)
        self.update_state(dx)

    def rk4_step(self, ax, delta):
        """ Runge-Kutta 4th order integration method. """
        k1 = self.compute_dx(ax, delta)
        self.update_state(k1, scale=0.5)
        
        k2 = self.compute_dx(ax, delta)
        self.update_state(k2, scale=0.5, revert=k1)
        
        k3 = self.compute_dx(ax, delta)
        self.update_state(k3, scale=1, revert=k2)

        k4 = self.compute_dx(ax, delta)
        
        # Combine k1, k2, k3, k4 for RK4 update
        dx = (k1 + 2*k2 + 2*k3 + k4) / 6
        self.update_state(dx)

    def compute_dx(self, ax, delta):
        """ Compute the state derivatives using the chosen model. """
        if self.model == "kinematic":
            return self.kinematic_model(ax, delta)
        elif self.model == "linear":
            return self.linear_single_track_model(ax, delta)
        elif self.model == "nonlinear":
            return self.nonlinear_single_track_model(ax, delta)

    def update_state(self, dx, scale=1, revert=None):
        """ Update state with scaled dx. Optionally revert previous state for RK4. """
        if revert is not None:
            self.x -= revert[0] * self.dt
            self.y -= revert[1] * self.dt
            self.theta -= revert[2] * self.dt
            self.vx -= revert[3] * self.dt
            self.vy -= revert[4] * self.dt
            self.r -= revert[5] * self.dt

        self.x += dx[0] * self.dt * scale
        self.y += dx[1] * self.dt * scale
        self.theta += dx[2] * self.dt * scale
        self.vx += dx[3] * self.dt * scale
        self.vy += dx[4] * self.dt * scale
        self.r += dx[5] * self.dt * scale
