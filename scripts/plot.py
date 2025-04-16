import numpy as np
import matplotlib.pyplot as plt
import ast
STEP0 = 15000 # Subsampling rate
STEP1 = 15500
zero_len = 4500

def get_data(file):
    x, y = [], []
    with open(file, "r") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                timestamp, vector_str = line.split(' ', 1)
                vector = ast.literal_eval(vector_str)

                # Flatten if it's a list of list
                if isinstance(vector[0], list):
                    vector = vector[0]

                x.append(vector[0])
                y.append(vector[1])
            except Exception as e:
                print(f"Error processing line: {line}")
                print(f"Exception: {e}")
    return x, y

def moving_average(data, window):
    return np.convolve(data, np.ones(window)/window, mode='valid')

# Load data (you can adapt this to paths in your environment)
file_r = '../profile/position_Ball.txt'
file_b = '../profile/position_NAO1.txt'
pred_r = '../profile/causal_Ball_nn.txt'
pred_b = '../profile/causal_NAO_nn.txt'

x_r, y_r = get_data(file_r)
x_b, y_b = get_data(file_b)
px_r, py_r = get_data(pred_r)
px_b, py_b = get_data(pred_b)

# Truncate lengths to match
min_len_r = min(len(x_r), len(px_r))
min_len_b = min(len(x_b), len(px_b))


x_r, y_r, px_r, py_r = x_r[zero_len:min_len_r], y_r[zero_len:min_len_r], px_r[zero_len:min_len_r], py_r[zero_len:min_len_r]
x_b, y_b, px_b, py_b = x_b[zero_len:min_len_b], y_b[zero_len:min_len_b], px_b[zero_len:min_len_b], py_b[zero_len:min_len_b]



# Compute errors
err_r = np.sqrt((np.array(px_r) - np.array(x_r))**2 + (np.array(py_r) - np.array(y_r))**2)
err_b = np.sqrt((np.array(px_b) - np.array(x_b))**2 + (np.array(py_b) - np.array(y_b))**2)

# Subsample
x_r_s, y_r_s = x_r[STEP0:STEP1], y_r[STEP0:STEP1]
px_r_s, py_r_s = px_r[STEP0:STEP1], py_r[STEP0:STEP1]
x_b_s, y_b_s = x_b[STEP0:STEP1], y_b[STEP0:STEP1]
px_b_s, py_b_s = px_b[STEP0:STEP1], py_b[STEP0:STEP1]

# Smooth errors
smoothed_err_r = moving_average(err_r, 1000)
smoothed_err_b = moving_average(err_b, 1000)

x_r_s = moving_average(x_r_s, 10)
y_r_s = moving_average(y_r_s, 10)
px_r_s = moving_average(px_r_s, 10)
py_r_s = moving_average(py_r_s, 10)
x_b_s = moving_average(x_b_s, 10)
y_b_s = moving_average(y_b_s, 10)
px_b_s = moving_average(px_b_s, 10)
py_b_s = moving_average(py_b_s, 10)
# Plotting
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))

# --- Trajectory Plot ---
# Actual paths
ax1.plot(x_r_s, y_r_s, color='pink', label='Ball')
ax1.plot(x_b_s, y_b_s, color='blue', label='Kicker')

# Predicted paths
ax1.plot(px_r_s, py_r_s, '--', color='red', label='Pred Ball')
ax1.plot(px_b_s, py_b_s, '--', color='cyan', label='Pred Kicker')

# Start and end points
ax1.scatter([x_r[0]], [y_r[0]], c='m', marker='<', s=100, label='Start Ball')
ax1.scatter([x_r[-1]], [y_r[-1]], c='pink', marker='D', s=100, label='End Ball')
ax1.scatter([x_b[0]], [y_b[0]], c='darkslateblue', marker='<', s=100, label='Start Kicker')
ax1.scatter([x_b[-1]], [y_b[-1]], c='indigo', marker='D', s=100, label='End Kicker')

# Direction arrows (every 5th step)
for i in range(0, len(x_r_s)-1, 5):
    ax1.annotate('', xy=(x_r_s[i+1], y_r_s[i+1]), xytext=(x_r_s[i], y_r_s[i]),
                 arrowprops=dict(arrowstyle='->', color='pink', lw=0.5))
for i in range(0, len(x_b_s)-1, 5):
    ax1.annotate('', xy=(x_b_s[i+1], y_b_s[i+1]), xytext=(x_b_s[i], y_b_s[i]),
                 arrowprops=dict(arrowstyle='->', color='blue', lw=0.5))

ax1.set_title("Trajectory")
ax1.set_xlabel("x")
ax1.set_ylabel("y")
ax1.grid(True, linestyle='--', alpha=0.3)
ax1.legend()

# --- Error Plot ---
time_r = range(len(smoothed_err_r))
time_b = range(len(smoothed_err_b))

ax2.plot(time_r, smoothed_err_r, label='Ball Error (smoothed)', color='orange')
ax2.plot(time_b, smoothed_err_b, label='Kicker Error (smoothed)', color='green')

# Mean lines
ax2.axhline(np.mean(err_r), color='orange', linestyle=':', label=f'Ball Avg: {np.mean(err_r):.2f}')
ax2.axhline(np.mean(err_b), color='green', linestyle=':', label=f'Kicker Avg: {np.mean(err_b):.2f}')

# Max point annotation
max_idx_r = np.argmax(err_r)
ax2.annotate(f"Max: {err_r[max_idx_r]:.2f}", 
             xy=(max_idx_r, err_r[max_idx_r]), 
             xytext=(max_idx_r+1, err_r[max_idx_r]+0.5),
             arrowprops=dict(arrowstyle='->', color='orange'), fontsize=8)

ax2.set_title("Prediction Error Over Time")
ax2.set_xlabel(f'Time Step')
ax2.set_ylabel('Euclidean Error')
ax2.grid(True, linestyle='--', alpha=0.3)
ax2.legend()

# Save and show
plt.tight_layout()
plt.savefig('trajectory_and_error_lineplot_improved.pdf', dpi=300)
plt.show()
