---
title: 'OSMoGrid: Generating Electrical Power Distribution System Models from Open Data'
tags:
  - Open Data
authors:
  - name: Johannes Hiry
    orcid: 0000-0002-1447-0607
    affiliation: 1
  - name: Chris Kittl
    orcid: 0000-0002-1187-0568
    affiliation: 1
  - name: Thomas Oberließen
    orcid: 0000-0001-5805-5408
    affiliation: 1
affiliations:
 - name: ie<sup>3</sup> - Institute of Energy Systems, Energy Efficiency and Energy Economics, TU Dortmund University, Germany
date: 27 October 2021
bibliography: joss-introduction.bib
---

# Summary
The Open Street Map Grid model Generator -- shortly _OSMoGrid_ -- is a Java-based tool to derive life-like models of electric power distribution systems.
It's focus is on the low voltage grid level, serving single housholds and other smaller electricity customers, like shops etc.
The software simulates a practical, conventional planning process that a distribution system operator would go through, if there wouldn't be any infrastructure available in the targeted area of interest (_green field planning_ principle).
For this purpose, it combines information from the Open Stree Map project and thorough assumptions based on expert knowledge on actual distribution grid planning processes.
Moreover, partially available information on existing infrastructure can be incoroporated as well (_brown field planning_ principle).

Obviously, _OSMoGrid_ aids contemporary power system research activities in providing life-like distribution system models with a geo-referenced grid structure within a user chosen area of interest -- this is a novelty by itself.
Moreover, by its modular nature, it also allows for research on a meta level.
Possible applications in this regard are, but not limited to:
-   Improved planning processes for a voltage level-integrated distribution grid planning
-   Sensitivity assessments of different assumptions on the overall grid performance
-   Research on distance based clustering algorithms to determine resonable and efficient supply areas for secondary substations
-   ...

# Statement of need

# Acknowledgements

Thanks to whom it may concern.

# References