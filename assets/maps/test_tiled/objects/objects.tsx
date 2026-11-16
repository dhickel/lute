<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.12.2" name="objects" tilewidth="160" tileheight="256" tilecount="2" columns="0">
 <grid orientation="orthogonal" width="1024" height="1"/>
 <tile id="6" type="PhysicObject">
  <properties>
   <property name="atlasKey" value="house"/>
  </properties>
  <image source="house.png" width="160" height="224"/>
  <objectgroup draworder="index" id="3">
   <object id="2" x="24.6667" y="72.6667" width="113.333" height="138"/>
  </objectgroup>
 </tile>
 <tile id="7" type="AnimatedPhysicObject">
  <properties>
   <property name="animation" type="class" propertytype="Animation">
    <properties>
     <property name="atlasKey" value="old_tree1/old_tree1"/>
     <property name="frameInterval" type="float" value="0.1"/>
     <property name="soundFrame" type="int" value="0"/>
     <property name="speed" type="float" value="0.35"/>
    </properties>
   </property>
  </properties>
  <image source="old_tree1/old_tree1_00.png" width="160" height="256"/>
  <objectgroup draworder="index" id="2">
   <object id="1" x="66.3333" y="192.167" width="29.5" height="31.9167">
    <ellipse/>
   </object>
  </objectgroup>
 </tile>
</tileset>
