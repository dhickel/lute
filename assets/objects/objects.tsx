<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.12.2" name="objects" tilewidth="160" tileheight="256" tilecount="3" columns="0">
 <grid orientation="orthogonal" width="1024" height="1"/>
 <properties>
  <property name="atlasAsset" value=""/>
 </properties>
 <tile id="4">
  <properties>
   <property name="animation" value="IDLE"/>
   <property name="animationSpeed" type="float" value="1"/>
   <property name="atlasAsset" value="OBJECTS"/>
   <property name="atlasKey" value="player"/>
   <property name="hasCamera" type="bool" value="true"/>
   <property name="isAnimated" type="bool" value="true"/>
   <property name="isPhysics" type="bool" value="true"/>
   <property name="keyFrames" type="int" value="8"/>
   <property name="physicBodyType" value="dynamic"/>
   <property name="speed" type="float" value="4"/>
  </properties>
  <image source="player.png" width="64" height="64"/>
  <objectgroup draworder="index" id="7">
   <object id="10" x="20.125" y="38.125" width="21.75" height="8.875">
    <ellipse/>
   </object>
  </objectgroup>
 </tile>
 <tile id="6">
  <properties>
   <property name="atlasAsset" value="OBJECTS"/>
   <property name="atlasKey" value="house"/>
  </properties>
  <image source="house.png" width="160" height="224"/>
 </tile>
 <tile id="7">
  <properties>
   <property name="animation" value="DEFAULT"/>
   <property name="animationSpeed" type="float" value="0.35"/>
   <property name="atlasAsset" value="OBJECTS"/>
   <property name="atlasKey" value="old_tree1/old_tree1"/>
   <property name="isAnimated" type="bool" value="true"/>
   <property name="isPhysics" type="bool" value="true"/>
   <property name="keyFrames" type="int" value="10"/>
   <property name="physicBodyType" value="static"/>
  </properties>
  <image source="old_tree1/old_tree1_00.png" width="160" height="256"/>
  <objectgroup draworder="index" id="2">
   <object id="1" x="62.3333" y="195.667" width="40" height="37.6667">
    <ellipse/>
   </object>
  </objectgroup>
 </tile>
</tileset>
