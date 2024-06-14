import numpy as np
import matplotlib.pyplot as plt


#Plota trajetoria 

i = 0
file_r = '../profile/position_red.txt'
file_b = '../profile/position_blue.txt'
pred_r = '../profile/causal_red_nn.txt'
pred_b = '../profile/causal_blue_nn.txt'
debug = True 

def get_data(file):
	x = []
	y = []

	with open(file,"r") as f: # Open file
		name = file.split('.')[-2] 
		if debug: 
			print(f"name.split('/') len: {len(name.split('/'))}")
			print(name)
			print(name.split('/'))
		data = f.readlines()
	
	for line in data: 
		col = line.split(' ') 
		id = col[0]
		x.append(float(col[1]))
		y.append(float(col[2]))
	return x,y 

def remove_strings_from_file(file_name, strings_to_remove):
    try:
        with open(file_name, 'r') as file:
            lines = file.readlines()

        # Open a new file for writing
        with open(file_name, 'w') as f:
            # Iterate through each line in the input file
            
            for line in lines:
                line = line.replace("     ", " ")
                line = line.replace("    ", " ")
                line = line.replace("   ", " ")
                line = line.replace("  ", " ")
                # Split the line by whitespace and keep only the elements from index 1 onwards
                elements = line.split(' ')[1:]
                # Join the elements back into a string and write to the output file
                f.write(' '.join(elements))
                
        with open(file_name, 'w') as file:
            for line in lines:
                for string_to_remove in strings_to_remove:
                    line = line.replace(string_to_remove, '')
                    #line = line.replace(',', '\n')
                file.write(line)
        
        print("Strings removed successfully!")
    except FileNotFoundError:
        print(f"File '{file_name}' not found.")

## Clean Data

# List of strings to remove
strings_to_remove = [
    ",","[","]","Exp number:", "Action num: ", "Battery: ", "reward: ",
    "Curiosity_lv: ", "Red: ", "Green: ", "Blue: ","action:","mot_value: ",
    "r_imp: ","g_imp: ","b_imp: ", "hug_drive: ", "cur_drive: "
]

remove_strings_from_file(file_r, strings_to_remove)
remove_strings_from_file(file_b, strings_to_remove)
remove_strings_from_file(pred_r, strings_to_remove)
remove_strings_from_file(pred_b, strings_to_remove)

x_r, y_r = get_data(file_r)
print(len(x_r))
print(f"xr: {x_r[len(x_r)-1]}, yr: {y_r[len(y_r)-1]}")
x_b, y_b = get_data(file_b)
print(f"xb: {x_b[len(x_b)-1]}, yb: {y_b[len(y_b)-1]}")

px_r, py_r = get_data(pred_r)
print(f"pxr: {px_r[len(px_r)-1]}, py_r: {py_r[len(py_r)-1]}")
px_b, py_b = get_data(pred_b)
print(f"xb: {px_b[len(px_b)-1]}, py_b: {py_b[len(py_b)-1]}")

# Plot trajectory
fig, ax = plt.subplots(figsize=(6, 6))

posTupR = []
for xi,yi in zip(x_r,y_r):
    posTupR.append((xi,yi))
posX, posY = zip(*posTupR)

x_ini = x_r[0]
y_ini = y_r[0]

x_fim = x_r[len(x_r)-1]
y_fim = y_r[len(y_r)-1]


ax.scatter(x_r, y_r, c='pink', label = 'red ball', s=10)
ax.scatter(x_ini,y_ini,c='m',marker='<', s=100, zorder=3, label = 'Start red')
ax.scatter(x_fim,y_fim,c='pink',marker='D', s=100,  zorder=3, label = 'End red')


posTupB = []
for xi,yi in zip(x_b,y_b):
    posTupB.append((xi,yi))
posX, posY = zip(*posTupB)

x_ini = x_b[0]
y_ini = y_b[0]

x_fim = x_b[len(x_b)-1]
y_fim = y_b[len(y_b)-1]


ax.scatter(x_b, y_b, c='blue', label = 'blue ball', s=10)
ax.scatter(x_ini,y_ini,c='darkslateblue',marker='<', s=100, zorder=3, label = 'Start blue')
ax.scatter(x_fim,y_fim,c='indigo',marker='D', s=100,  zorder=3, label = 'End blue')

# Plot trajectory
posTupPR = []
for xi,yi in zip(px_r,py_r):
    posTupPR.append((xi,yi))
posX, posY = zip(*posTupR)

x_ini = px_r[0]
y_ini = py_r[0]

x_fim = px_r[len(px_r)-1]
y_fim = py_r[len(py_r)-1]


ax.scatter(px_r, py_r, c='red', label = 'pred red ball', s=10)
ax.scatter(x_ini,y_ini,c='r',marker='<', s=100, zorder=3, label = 'Start pred red')
ax.scatter(x_fim,y_fim,c='red',marker='D', s=100,  zorder=3, label = 'End pred red')

# Plot trajectory
posTupPB = []
for xi,yi in zip(px_b,py_b):
    posTupPB.append((xi,yi))
posX, posY = zip(*posTupB)

x_ini = px_b[0]
y_ini = py_b[0]

x_fim = px_b[len(px_b)-1]
y_fim = py_b[len(py_b)-1]


ax.scatter(px_b, py_b, c='cyan', label = 'pred blue ball', s=10)
ax.scatter(x_ini,y_ini,c='c',marker='<', s=100, zorder=3, label = 'Start pred blue')
ax.scatter(x_fim,y_fim,c='cyan',marker='D', s=100,  zorder=3, label = 'End pred blue')

ax.grid(True)
ax.legend(loc='best')
plt.title("Trajectory")
plt.xlabel("x")
plt.ylabel("y")
plt.savefig('trajectory'+'.pdf')
plt.show()
