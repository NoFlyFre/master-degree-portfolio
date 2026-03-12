import math

class PurePursuitController:
    def __init__(self, wheelbase, max_steer):
        """
        Initialize the Pure Pursuit controller.
        
        Args:
            wheelbase (float): Distance between the front and rear axles (L).
            max_steer (float): Maximum steering angle in radians.
        """
        self.wheelbase = wheelbase
        self.max_steer = max_steer

    def compute_steering_angle(self, target, actual_heading, Lf):
        # Calculate the heading error (alpha)
        alpha = math.atan2(target[1], target[0]) - actual_heading
        
        # Compute the steering angle (delta) using Pure Pursuit formula
        delta = math.atan2(2.0 * self.wheelbase * math.sin(alpha), Lf)

        # Saturate the steering angle within the maximum limits
        delta = max(-self.max_steer, min(delta, self.max_steer))
        
        return delta

