# Autonomous-Intersection-Management 
## Description
This project is an automated intersection management system, which can control and manage the intersection traffic without the help of traffic lights. It reduces the intersection delay by roughly 37% compared to the traffic lights systems. In addition, it allows pedestrians to cross the road with minimum waiting time.

The project was originally forked from [Movsim](https://github.com/movsim/movsim).

## Core Algorithm
This project leverages the idea of virtual platooning from the paper “Cooperative Intersection Control Based on Virtual Platooning”. Each vehicle within the intersection area is assigned with a preceding vehicle to follow. The preceding vehicle can be on the same road or different road.
<br/><br/>
<img src = "https://github.com/rtst777/Autonomous-Intersection-Management/blob/develop/img/virtual_platooning_visualization.png" width="700" height="300">

Once the vehicle receives the preceding vehicle information, it will keep following that vehicle with safe distance until it is assigned with a new preceding vehicle. If the vehicle has no preceding vehicle assigned, it will move with its constant desired speed.

## Implementation
The system consists of four major components:

<table>
  <tbody>
    <tr>
      <th align="center">Scenario Initializer</th>
      <th>Central Server</th>
    </tr>
    <tr>
      <td>
        <ul>
          <li>Initialize road networks</li>
          <li>Defines collision points</li>
          <li>Initialize vehicles</li>
        </ul>
      </td>
      <td>
        <ul>
          <li>Storage</li>
            <ul>
              <li>Stores vehicle’s dynamics</li>
              <li>Stores pedestrian information</li>
            </ul>
          <li>Preceding Vehicle Assigner</li>
            <ul>
              <li>Assign a host vehicle a preceding vehicle to follow</li>
            </ul>
        </ul>
      </td>
    </tr>
    <tr>
      <th align="center">Vehicle</th>
      <th align="center">Graphical User Interface</th>
    </tr>
    <tr>
      <td>
        <ul>
          <li>Control Mode Assigner</li>
            <ul>
              <li>Receive preceding vehicle info</li>
              <li>Decide control mode</li>
            </ul>
          <li>Updater</li>
            <ul>
              <li>Adjust acceleration</li>
            </ul>
          <li>Reassignment Evaluator</li>
            <ul>
              <li>Request for updating preceding vehicle info</li>
            </ul>
        </ul>
      </td>
      <td>
        <ul>
          <li>Format Converter</li>
            <ul>
              <li>Convert backend data to appropriate form</li>
            </ul>
          <li>Graphical Display</li>
            <ul>
              <li>Visualize the converted data</li>
            </ul>
          <li>User Interaction Handler</li>
            <ul>
              <li>Allow user to interact with the elements in graphical display</li>
            </ul>
        </ul>
      </td>
    </tr>
  </tbody>
</table>

<br/>
With the following system-level diagram: <br/><br/>
<img src = "https://github.com/rtst777/Autonomous-Intersection-Management/blob/develop/img/system_design_diagram.jpg" width="430" height="500">


## Video Demo
- [demo with high traffic load](https://youtu.be/sH3L4d5EbE0)
- [demo with pedestrian](https://youtu.be/JaevMnvq1zc)
- [performance comparison](https://youtu.be/h8SUe_SF_aI)
