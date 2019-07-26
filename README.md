# Autonomous-Intersection-Management 
## Description
This project is an automated intersection management system, which can control and manage the intersection traffic without the help of traffic lights. It reduces the intersection delay by roughly 37% compared to the traffic lights systems. In addition, it allows pedestrians to cross the road with minimum waiting time.

The project was originally forked from [Movsim](https://github.com/movsim/movsim).

## Core Algorithm
This project leverages the idea of virtual platooning from the paper “”. Each vehicle within the intersection area is assigned with a preceding vehicle to follow. The preceding vehicle can be on the same road or different road.
<br>
<img src = "https://github.com/rtst777/Autonomous-Intersection-Management/blob/develop/img/virtual_platooning_visualization.png" width="700" height="300">

Once the vehicle receives the preceding vehicle information, it will keep following that vehicle with safe distance until it is assigned with a new preceding vehicle. If the vehicle has no preceding vehicle assigned, it will move with its constant desired speed.

## Implementation
The system consists of four major components.

Scenario Initializer
- Initialize road networks
- Defines collision points
- Initialize vehicles

Central Server
- Storage
  - stores vehicle’s dynamics
  - stores pedestrian information
- Preceding Vehicle Assigner
  - assign a host vehicle a preceding vehicle to follow

Vehicle
- Control Mode Assigner
  - receive preceding vehicle info
  - decide control mode 
- Updater
  - adjust acceleration
- Reassignment Evaluator
  - request for updating preceding vehicle info

Graphical User Interface
- Format Converter
  - convert backend data to appropriate form
- Graphical Display
  - visualize the converted data
- User Interaction Handler
  - allow user to interact with the elements in graphical display



