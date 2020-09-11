import React, { Component } from 'react';
import { Layout, Avatar, Menu } from 'antd';
import { DgPaper } from '../../joint/transformation/step.js';
import { classExtension } from '../classExtension.js';
import Halo from '../designer/Halo.js';
import Stencil from '../designer/Stencil.js';
import update from 'immutability-helper';
import SplitterLayout from 'react-splitter-layout';
import createComponent from '../Components';
import _ from 'lodash';
import {normalizeTransformationPorts} from '../../utils/transformationPorts';
import $ from "jquery";
import {clone} from '../../utils/meta';
import DescriptionBox from '../designer/DescriptionBox';

const { Sider, Content } = Layout;

class DesignerView extends Component {

    constructor(...args) {
      super(...args);
      this.defineSources();
      this.state = { collapsed: false, scalefactor: 1 }
      this.needReuildGraph = false
      this.cellsToDelete = []
      this.linksToDelete = []
      this.padding = 20
    }

    toggle() {
      this.setState({
        collapsed: !this.state.collapsed,
      });
    }

    deleteCashedLinks(updatedCells) {
        throw new Error("abstract!")
    }

    deleteCashed(cb){
        if(this.props.entity && (this.cellsToDelete.length > 0 || this.linksToDelete.length > 0)) {
            var updatedCells = {}
            this.cellsToDelete.forEach(cellEntity=>{
                var cellDefinition = classExtension[this.props.entity._type_].nodesDef.find((s) => {return s._type_ === cellEntity._type_} )
                updatedCells[cellDefinition.group] = updatedCells[cellDefinition.group] || this.props.entity[cellDefinition.group]
                const index = updatedCells[cellDefinition.group].indexOf(cellEntity)
                updatedCells[cellDefinition.group] = update(updatedCells[cellDefinition.group], {$splice: [[index, 1]]});
            })

            if(this.linksToDelete.length > 0) {
                this.deleteCashedLinks(updatedCells)
            }

            this.props.updateEntity(updatedCells, cb)

            this.cellsToDelete = []
            this.linksToDelete = []
        }
    }

    linkDeleted(link){
        if(!this.rebuildInProcess && link.attributes.entity) {
            console.log("linkDeleted")
            var linkEntity = link.attributes.entity
            this.linksToDelete.push(linkEntity)
            if(!this.inDelete){
                this.deleteCashed()
            }
        }
    }

    processLink(link, sourceCell, targetCell, sourceEntity, targetEntity, sourcePort, targetPort) {
        throw new Error("abstract!")
    }

    linkConnected(link){
        console.log("linkConnected")
        if(this.props.entity) {
            var sourceCell = this.graph.getCell(link.get("source").id)
            var targetCell = this.graph.getCell(link.get("target").id)
            var sourceEntity = sourceCell.get("entity")
            var targetEntity = targetCell.get("entity")
            var sourcePort = classExtension.findPortByPortId(this.props.entity._type_, sourceEntity, link.get("source").port)
            var targetPort = classExtension.findPortByPortId(this.props.entity._type_,targetEntity, link.get("target").port)

            this.processLink(link, sourceCell, targetCell, sourceEntity, targetEntity, sourcePort, targetPort)
        }
    }

    cellCreated(cell){
        console.log("cellCreated")
        var entity = cell.get("entity")
        if(this.props.entity) {
            var cellDefinition = classExtension[this.props.entity._type_].nodesDef.find((s) => {return s._type_ === entity._type_} )
            var editedArray = update(this.props.entity[cellDefinition.group] || [], {$push: [entity]})
            var n = {}
            n[cellDefinition.group] = editedArray
            this.props.updateEntity(n)
        }
    }

    cellDeleted(cell){
        if(!this.rebuildInProcess) {
            console.log("cellDeleted")
            var entity = cell.get("entity")
            this.cellsToDelete.push(entity)
            if(!this.inDelete){
                this.deleteCashed()
            }
        }
    }

    defineSources(){
        DgPaper.defineLink((link)=>this.linkDeleted(link))
        if(this.props.entity) {
            classExtension[this.props.entity._type_].nodesDef.map((cellDefinition)=>
                DgPaper.defineStep(cellDefinition.type, cellDefinition.label, cellDefinition.image, cellDefinition.ports)
            )
        }
    }

    updateNodeEntity(oldVal, newVal) {
        const cellDefinition = classExtension[this.props.entity._type_].nodesDef.find(sd=>sd._type_ === oldVal._type_)
        if (cellDefinition) {
            const idx = this.props.entity[cellDefinition.group].indexOf(oldVal)

            let n = update(this.props.entity, {
                    [cellDefinition.group]: {
                        [idx]: {$merge: newVal}
                    }
                }
            )
            return {entity: n, updated: n[cellDefinition.group][idx]}
        }
        return {entity: this.props.entity, updated: oldVal}
    }

    selectCellView(cellView){

        var sl = {highlighter: {
               name: 'addClass',
               options: {
                   className: 'bordered'
               }
           }}

        let beforeSelected = classExtension.nodes(this.props.entity).find(s=>s.transient && s.transient.selected === true)

        Object.values(this.paper._views).forEach((v)=>{
            v.unhighlight(null, sl)
        })

        var selectNew = ()=>{
            if(cellView) {
                cellView.highlight(null, sl);

                let entity = cellView.model.get("entity")

                let selected = update(entity, {$merge: {transient: {selected: true}}})

                let r = this.updateNodeEntity(entity, selected)
                this.updateCell(cellView.model, r.updated)
                this.props.updateEntity(r.entity,
                ()=>{
                    this.props.context.updateContext({selectedNode: classExtension.nodes(this.props.entity).find(n=>n.transient && n.transient.selected === true)})
                })
            }
        }
        if(beforeSelected) {
            if(cellView === null || beforeSelected !== cellView.model.get("entity")) {
                let unselected = update(beforeSelected, {transient: {$merge: {selected: false}}})
                let cell = this.graph.getCells().find(c=>c.get("entity") === beforeSelected)
                let r = this.updateNodeEntity(beforeSelected, unselected)
                this.updateCell(cell, r.updated)
                this.props.updateEntity(r.entity, selectNew)
            }
            if(cellView !== null && beforeSelected === cellView.model.get("entity")) {
                cellView.highlight(null, sl)
                this.props.context.updateContext({selectedNode: cellView.model.get("entity")})
            }
        } else {
            selectNew()
        }
        this.entityUpdated()
    }

    cellDblClick(cellView){
        console.log("cellDblClick")
    }

    cellMove(cellView, x, y){
        if(cellView.model.get("type") !== "devs.transformation.Flow") {
            this.setState({haloPosition: _.cloneDeep(cellView.model.get("position"))})
        }
    }

    buildLinks() {
        throw new Error("abstract!")
    }

    buildGraph(clear, saveMarkup){
        this.rebuildInProcess = true;
        const prevJson = this.graph.toJSON()

        var selected = classExtension.nodes(this.props.entity).find(n=>n.transient && n.transient.selected === true)
        var selectedKey;
        if(selected){
            selectedKey = selected._type_ + selected.name + selected.e_id
        }
        if(clear){
            this.graph.clear();
            this.graph.fromJSON({"cells":[]})
        }
        console.log("Rebuild")
        if(this.props.entity){
            var jsonViewObject
            if(this.props.entity.jsonView){
                jsonViewObject = JSON.parse(this.props.entity.jsonView)
            }
            var cells = []
            classExtension.nodes(this.props.entity)
                .forEach((step)=>{
                    var cellDefinition = classExtension[this.props.entity._type_].nodesDef.find(sd=>sd._type_ === step._type_)
                    if(cellDefinition){
                        var jsonCell
                        if(step.markup){
                            jsonCell = JSON.parse(step.markup)
                        } else {
                            jsonCell = jsonViewObject ? jsonViewObject.cells.find((c)=>{return (c.entity && (step.name === c.entity.name)) || (step.name === c.name)}) : undefined;
                            if(!jsonCell || saveMarkup){
                                jsonCell =  prevJson.cells.find((c)=>{return (c.entity && (step.name === c.entity.name)) || (step.name === c.name)}) || jsonCell
                            }

                        }
                        var cell = DgPaper.createCell(
                            cellDefinition,
                            jsonCell && jsonCell.position ? jsonCell.position.x : 0, jsonCell && jsonCell.position ? jsonCell.position.y : 0,
                            this.graph,
                            step,
                            jsonCell && jsonCell.size ? jsonCell.size.width : 0, jsonCell && jsonCell.size ? jsonCell.size.height : 0,
                        true)
                        cells.push(cell)
                    }
                });
                this.graph.addCells(cells);
            this.buildLinks();
        }
        if(selectedKey){
            var viewToSelect = Object.values(this.paper._views).find(v=>v.model.attributes.entity._type_ + v.model.attributes.entity.name + v.model.attributes.entity.e_id === selectedKey)
            this.selectCellView(viewToSelect)
        }

        this.needReuildGraph = false
        this.saveMarkup = undefined
        this.rebuildInProcess = false
        this.forceUpdate()
    }

    componentWillUnmount(){
        console.log("componentWillUnmount")
        this.props.unregisterEntityCustomizer(this.jsovViewCustomizerInstance)
        this.props.unregisterEntityUpdater(normalizeTransformationPorts)
        this.props.context.updateContext({selectNodeInDesigner: undefined})
        this.paper = null;
        this.graph = null;
    }

    handleEditActionCancel(){
        this.setState({editAction: null})
    }

    delete(entity) {
        var cell = this.graph.getCells().find(c=>c.get("entity") === entity)
        this.inDelete = true
        this.graph.removeLinks(cell)
        this.cellDeleted(cell)
        this.graph.removeCells(cell)
        this.deleteCashed(()=>{
            this.setState({selected: null});
            this.inDelete = false;
        })
    }

    duplicate(entity) {
        var cell = this.graph.getCells().find(c=>c.get("entity") === entity)
        var def = classExtension[this.props.entity._type_].nodesDef.find(n=>n._type_ === entity._type_)
        var step = clone(entity)
        step.name = def.label.replace(" ", "_") + "_" + this.graph.getCells().length
        step.label = step.name
        this.cellCreated(
            DgPaper.createCell(def, cell.get("position").x + 20,
            cell.get("position").y + 20, this.graph,
            step, cell.get("size").width,
            cell.get("size").height)
        )
    }

    render() {
        console.log("render")
        if(this.props.active === true) {
            this.checkGraphConsistents()
            if(this.needReuildGraph === true) {
                if(this.graph) {
                    if(!this.rebuildInProcess){
                        this.rebuildInProcess = true;
                        setTimeout(()=>this.buildGraph(true, this.saveMarkup === false ? false : true), 0)
                    }
                }
            }
        }
        var selected = classExtension.nodes(this.props.entity).find(n=>n.transient && n.transient.selected === true)
        let selectedDef = null
        let selectedIndex = null
        if(selected) {
            selectedDef = classExtension[this.props.entity._type_].nodesDef.find(n=>n._type_ === selected._type_)
            selectedIndex = this.props.entity[selectedDef.group].indexOf(selected)
        }

        var haloPosition;

        var selectedCell = null
        if(this.graph && selected) {
            selectedCell = this.graph.getCells().find(cell=>cell.get("entity") === selected)
            var selectedCellView = Object.values(this.paper._views).find(v=>v.model === selectedCell)
            if(selectedCellView){
                haloPosition = {
                    x: (selectedCell.attributes.position.x + (this.paper._viewportMatrix ? this.paper._viewportMatrix.e : 0)) * this.state.scalefactor,
                    y: (selectedCell.attributes.position.y + (this.paper._viewportMatrix ? this.paper._viewportMatrix.f : 0)) * this.state.scalefactor,
                    scalefactor: this.state.scalefactor,
                    width: selectedCellView.model.attributes.size.width,
                    height: selectedCellView.model.attributes.size.height
                }
            } else {
                haloPosition = undefined
            }
        }

        if(!haloPosition && this.state.haloPosition) {
            haloPosition = this.state.haloPosition;
        }

        return(
            <Layout>
              <Sider trigger={null} collapsible collapsed={this.state.collapsed} className={"designer-sider"}>
                {!this.state.collapsed &&
                <Menu mode="inline" onClick={()=>{this.toggle()}} selectable={false}>
                    <Menu.Item>
                      <Avatar src={this.state.collapsed ? "images/icon-core/menu-unfold.svg" : "images/icon-core/menu-fold.svg"} shape="square" size={"large"} />
                      <span>{this.state.collapsed ? "" : "Hide"}</span>
                    </Menu.Item>
                </Menu>}
                <Stencil nodesDefs={classExtension[this.props.entity._type_].nodesDef} collapsed={this.state.collapsed} toggle={()=>this.toggle()}/>
              </Sider>
              <Layout>
                <Content className={"designer-content"}>
                    <SplitterLayout vertical={false} primaryIndex={0} secondaryInitialSize={300} customClassName={"splitter-layout-custom"}>
                        <div id="paperContainer">
                            {this.graph && <DescriptionBox
                                key="transDesc"
                                description={_.get(this.props.entity, 'description', undefined)}
                                onChange={(newDescription, e)=>{
                                    this.props.updateEntity({ description: newDescription })
                                }}
                            >
                                <Avatar 
                                    className={this.props.entity.description ? "designer-description" : "designer-description-empty"}
                                    style={{
                                        position: 'absolute',
                                        width: '35px',
                                        height: '35px',
                                        left: '0px',
                                        top: '0px'
                                    }}
                                    src={`images/icon-core/info.svg`} 
                                />
                            </DescriptionBox>}
                            {selectedCell &&
                                <Halo
                                selected={selected}
                                cellDefinition={selectedDef}
                                position={haloPosition}
                                deleteCell={(selected)=>this.delete(selected)}
                                duplicateCell={(selected)=>this.duplicate(selected)}
                                graph={this.graph}
                                onEditAction={(action, entity) => {this.props.openEditor(action, entity)}}/>
                            }
                            {this.graph && this.graph.getCells().map(cell => {
                                    return cell.attributes.position &&
                                    <DescriptionBox
                                        key={cell.attributes.entity.name}
                                        description={_.get(cell.attributes.entity, 'description', undefined)}
                                        onChange={(newDescription, e)=>{
                                            const nodesDef = classExtension[this.props.entity._type_].nodesDef
                                            const group = nodesDef.find(n=>n._type_ === cell.attributes.entity._type_).group
                                            const entityIndex = this.props.entity[group].findIndex(e => e.e_id === cell.attributes.entity.e_id)
                                            const updated = update(
                                                this.props.entity[group],
                                                {[entityIndex]: { $merge: { description: newDescription }} }
                                            )
                                            this.props.updateEntity({ [group]: updated })
                                        }}
                                    >
                                        <Avatar 
                                            className={cell.attributes.entity.description ? "designer-description" : "designer-description-empty"}
                                            style={{
                                                position: 'absolute',
                                                width: '25px',
                                                height: '25px',
                                                left: `${(cell.attributes.position.x + (this.paper._viewportMatrix ? this.paper._viewportMatrix.e : 0)) * this.state.scalefactor}px`,
                                                top: `${(cell.attributes.position.y + (this.paper._viewportMatrix ? this.paper._viewportMatrix.f : 0)) * this.state.scalefactor}px`
                                            }}
                                            src={`images/icon-core/info.svg`} 
                                        />
                                    </DescriptionBox>
                                })
                            }
                        </div>
                        <div id="objectInspector">
                            {selected &&
                                (createComponent(
                                    selectedDef.editor ? selectedDef.editor : "ObjectInspector",
                                    {...{...this.props,
                                        entity: {...selected, __parent: this.props.entity},
                                        updateEntity: e => {
                                            selected = this.props.entity[selectedDef.group][selectedIndex]
                                            var r = this.updateNodeEntity(selected, e)
                                            selectedCell = this.graph.getCells().find(cell=>cell.get("entity") === selected)
                                            this.needReuildGraph = !this.updateCell(selectedCell, r.updated, classExtension[this.props.entity._type_].nodesDef.find((s) => {return s._type_ === r.updated._type_}))
                                            this.props.updateEntity(r.entity, ()=>this.entityUpdated())
                                        }
                                    }}
                                ))
                            }
                        </div>
                    </SplitterLayout>
                </Content>
              </Layout>
            </Layout>)
     }

     entityUpdated() {

     }

     initGraph() {
         var g = DgPaper.buildGraph(
             (cell)=>{this.cellCreated(cell)},
             (link)=>{this.linkConnected(link)},
             (view)=>{this.selectCellView(view)},
             (cellView)=>{this.cellDblClick(cellView)},
             (cellView, x, y)=>{this.cellMove(cellView, x, y)},
             (cellView)=>{alert("description")}
            )
         this.paper = g.paper
         this.graph = g.graph
     }

     componentDidMount(){
         this.initGraph()
         this.buildGraph(false, false)
         this.jsovViewCustomizerInstance = (value)=>this.jsovViewCustomizer(value, "jsonView")
         this.props.registerEntityCustomizer(this.jsovViewCustomizerInstance)
         this.props.registerRefresh(()=>{this.needReuildGraph = true;this.saveMarkup = false;})
         if(this.props.activeObject._type_ === "etl.Transformation") {
             this.props.registerEntityUpdater(normalizeTransformationPorts)
         }
         this.props.listButtons([
             {id: "zoomin", tooltip: 'zoom in', icon: "images/icon-core/zoomin-modern.svg", onClick: ()=>this.fitToContent(this.state.scalefactor + 0.1)},
             {id: "zoomout", tooltip: 'zoom out', icon: "images/icon-core/zoomout-modern.svg", onClick: ()=>this.fitToContent(this.state.scalefactor - 0.1)},
             {id: "zoomnorm", tooltip: 'adjust', icon: "images/icon-core/align-modern.svg", onClick: ()=>{
                let r = this.paper.getContentBBox()

                this.graph.startBatch("move")
                this.graph.getCells().forEach(cell=>{
                    if(!cell.isLink()){
                        cell.position(cell.position().x - r.x + this.padding, cell.position().y - r.y + this.padding)
                    }
                })
                this.graph.stopBatch("move")
                $('#paperContainer').parent().animate({
                    scrollTop: 0, scrollLeft: 0
                }, 500);
            }},
            {id: "rearrange", tooltip: 'arrangeLR', icon: "images/icon-core/arrange-modern.svg", onClick: ()=>{
                DgPaper.rearrange(this.graph);
                $('#paperContainer').parent().animate({
                    scrollTop: 0, scrollLeft: 0
                }, 500);
            }}
         ])
         this.props.context.updateContext({selectNodeInDesigner: (node) => {
             if(this && this.paper && this.graph) {
                 let cell = this.graph.getCells().find(c=>c.get("entity") === node)
                 let view = this.paper.findViewByModel(cell)
                 this.selectCellView(view)
             }
         }})

     }

     fitToContent(scalefactor, fit) {
         this.setState({scalefactor: scalefactor})
         this.paper.scale(scalefactor, scalefactor)
         var r = this.paper.getContentBBox()
         if(fit || (r.x < 0 || r.y < 0)) {
             this.paper.fitToContent({padding: this.padding, minWidth: 2000, minHeight: 2000, allowNewOrigin: 'any'});
             this.setState({scalefactor: this.paper._viewportMatrix.a})
         }
     }

     updateLinks(cell, oldEnt, entity) {

     }

     updateCell(cell, entity, classDef) {
         var success = true;
         cell.attr(".label/text", entity.label || entity.name)
         let oldEnt = cell.attributes.entity
         cell.attributes.entity = entity
         cell.attributes.name = entity.name
         if(classDef) {
             var old = this.rebuildInProcess
             this.rebuildInProcess = true;
             success = DgPaper.updatePorts(cell, classDef.ports, entity)
             this.rebuildInProcess = old
         }
         this.updateLinks(cell, oldEnt, entity)
/*         let cellLinks = this.graph.getConnectedLinks(cell)
         cellLinks.map(l=> {
             if(l.attributes.entity.start === oldEnt) {
                 l.attributes.entity.start = entity
             }
             if(l.attributes.entity.finish === oldEnt) {
                 l.attributes.entity.finish = entity
             }
             return l
         })*/
         return success
     }

     checkGraphConsistents() {
         throw new Error("abstract!")
     }

     deleteProp(value, propName, parent) {
         if(typeof value !== "object"){
             if(!Array.isArray(value)) {
                 return value
             }
         }
         if(!Array.isArray(value)) {
             if(value){
                if(value.hasOwnProperty(propName)) {
                    delete value[propName]
                }
                Object.entries(value).forEach((e)=>{this.deleteProp(e[1], propName, parent + "." + e[0])})
             }
         } else {
             value.forEach((node, index)=>{
                 this.deleteProp(node, propName, parent + '[' + index + ']')
             })
         }
         return value
     }

     jsovViewCustomizer(value, propToDelete) {
         var data = this.graph.toJSON();
         var data2 = _.cloneDeepWith(data, (value)=>this.deleteProp(value, "entity"));
         var e = _.cloneDeep(value)
         e.jsonView = JSON.stringify(data2);
         return e
     }

}

export default DesignerView;
