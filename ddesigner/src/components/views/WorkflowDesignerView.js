import { translate } from 'react-i18next'
import { DgPaper } from '../../joint/transformation/step.js';
import update from 'immutability-helper';
import DesignerView from './DesignerView.js'
import data from '../designer/designer.json';
import LinkDescription from '../../classes/LinkDescription';
import WFPortDescription from '../../classes/WFPortDescription';

class WorkflowDesignerView extends DesignerView {

    addNewSourcePortAndLinkToIt(link, sourceCell, sourceEntity, targetEntity) {
        if(link.get("source").port !== "addNew") {
            throw new Error("need point to add new")
        }
        let portDef = sourceCell.attributes.ports.items.find(p=>p.id === link.get("source").port).portDef
        if(!portDef.attribute) {
            throw new Error("need attribute to add link")
        }
        if(portDef.attribute2 && !portDef._type_) {
            throw new Error("need class to add link item")
        }
        var newLinkObject
        if(!portDef.attribute2) {
            newLinkObject = targetEntity
        } else {
            newLinkObject = {_type_: portDef._type_, [portDef.attribute2]: targetEntity}
        }
        let collection = update(sourceEntity[portDef.attribute], {$push: [newLinkObject]})
        let r = this.updateNodeEntity(sourceEntity, update(sourceEntity, {$merge: {[portDef.attribute]: collection}}))
        this.props.updateEntity(r.entity)
    }

    processLink(link, sourceCell, targetCell, sourceEntity, targetEntity, sourcePort, targetPort) {
        let sourceDefinition = data.workflowNodes.find((s) => {return s._type_ === sourceEntity._type_})
        let cellPort = sourceCell.attributes.ports.items.find(p=>p.id === link.attributes.source.port)
        let sourcePortDef = cellPort.multiple ? sourceDefinition.ports.find(p=>p.group==="out") : sourceDefinition.ports.find(p=>p.id === link.get("source").port && p.group === "out")
        var r;
        if(sourcePortDef.multiple){
            var idx = Number(link.get("source").port);
            if(link.get("source").port === "addNew") {
                this.addNewSourcePortAndLinkToIt(link, sourceCell, sourceEntity, targetEntity)
                return
            }

            if(sourcePortDef.attribute2) {
                r = this.updateNodeEntity(sourceEntity, update(sourceEntity, {
                    [sourcePortDef.attribute]: {
                        [idx]: {$merge: {[sourcePortDef.attribute2]: targetEntity}}
                    }
                }))
            } else {
                r = this.updateNodeEntity(sourceEntity, update(sourceEntity, {
                    [sourcePortDef.attribute]: {
                        [idx]: {$set: targetEntity}
                    }
                }))
            }
        } else {
            r = this.updateNodeEntity(sourceEntity, update(sourceEntity, {$merge: {[sourcePortDef.attribute]: targetEntity}}))
        }
        link.attributes.entity = new LinkDescription(r.updated, targetEntity, link.attributes.source.port, link.attributes.target.port)
        sourceCell.attributes.entity = r.updated
        this.props.updateEntity(r.entity)
    }

    getTransitions(){
        let entity = this.props.entity
        var transitions = [];
        var ports = [];
        (entity.nodes || []).forEach(node=>{
            let def = data.workflowNodes.find(d=>d._type_ === node._type_)

            def.ports.filter(d=>d.group === "out").forEach(d=>{
                let portItems = []
                if(Array.isArray(node[d.attribute])) {
                    portItems = portItems.concat(node[d.attribute])
                } else {
                    portItems = [node[d.attribute]]
                }

                portItems.filter(p=>p && p._type_).forEach(p=>{
                    let portD = new WFPortDescription(d, p, node)
                    ports.push(portD)
                })
            })
        });

        ports.filter(pi=>pi.getTargetNode()).map(pi=>{
            return transitions.push(new LinkDescription(pi.node, pi.getTargetNode(), pi.getId(), "in"))
        })

        return transitions;
    }

    deleteCashedLinks(updatedCells) {
        if(this.linksToDelete.length > 0) {
            var nodes = updatedCells.nodes || this.props.entity["nodes"]
            this.linksToDelete.forEach(linkEntity=>{
                var idx = nodes.indexOf(linkEntity.start)
                if(idx > -1){
                    var sourceDef = data.workflowNodes.find((s) => {return s._type_ === linkEntity.start._type_})
                    sourceDef.ports.filter(p=>p.group==="out" && (p.id === linkEntity.startPortId || p.multiple)).forEach(p=>{
                        if(!p.multiple){
                            nodes = update(nodes, {[idx]: {$merge: {[p.attribute]: undefined}}})
                        } else {
                            let idxToDelete = Number(linkEntity.startPortId)
                            let emptiedPorts = update(linkEntity.start, {[p.attribute]: {$splice: [[idxToDelete, 1]]}})
                            nodes = update(nodes, {[idx]: {$merge: emptiedPorts}})
                        }
                    })
                }
            })
            updatedCells.nodes = nodes
        }
    }

    createLinks() {
        const links = this.getTransitions() || []
        var cells = []
        links.forEach((t) => {
            let link = DgPaper.createLink(t.start, t.finish, t.startPortId, t.finishPortId, this.graph, t, true)
            if(link){
                cells.push(link)
            }
        })
        return cells
    }

    buildLinks() {
        this.graph.addCells(this.createLinks())
    }

    updateLinks(cell, oldEnt, entity) {
        let cellLinks = this.graph.getConnectedLinks(cell)
         cellLinks.map(l=> {
             if(l.attributes.entity.start === oldEnt) {
                 l.attributes.entity.start = entity
             }
             if(l.attributes.entity.finish === oldEnt) {
                 l.attributes.entity.finish = entity
             }
             return l
         })
    }

    entityUpdated() {
        let links = this.createLinks()
        if(this.checkLinks(links) === false) {
            console.log("recreate links")
            this.rebuildInProcess = true
            this.graph.removeCells(this.graph.getLinks())
            this.graph.addCells(links)
            this.rebuildInProcess = false
        }
    }

    checkLinks(createdLinks) {
        let existedLinks = this.graph.getLinks()
        if(createdLinks.length !== existedLinks.length) {
            return false;
        }

        var result = true
        existedLinks.forEach(link => {
            let existedLinkDescription = link.attributes.entity
            let createdLink = createdLinks.find(t=>{
                let createdLinkDescription = t.attributes.entity;
                let startF = createdLinkDescription.start === existedLinkDescription.start;
                let finishF = createdLinkDescription.finish === existedLinkDescription.finish;
                let startPortF = createdLinkDescription.startPortId === existedLinkDescription.startPortId;
                let finishPortIdF = createdLinkDescription.finishPortId === existedLinkDescription.finishPortId;
                return startF && finishF && startPortF && finishPortIdF}
            )
            if(!createdLink) {
                result = false
            }
        })
        return result
    }

    checkGraphConsistents() {
        console.log("checkGraphConsistents")
        if(!this.graph){
            return;
        }
        if(this.needReuildGraph === true) {
            return;
        }
        this.needReuildGraph = false
        var cells = this.graph.getCells().filter(cell=>cell.attributes.entity)
        if(this.props.entity) {
           const nodes = (this.props.entity.nodes || [])
           var transitions = this.getTransitions() || [];
           var nodesCount = nodes.length + transitions.length
           if(nodesCount !== cells.length){
               this.needReuildGraph = true
               return
           }
           nodes.forEach((e)=>{
               var entityCell = cells.find((c) => {return c.attributes.entity === e})
               var cellById = cells.filter(c=>c.attributes.entity).find((c) => {
                   return c.attributes.entity.e_id && c.attributes.entity.e_id === e.e_id
               })
               if(!entityCell) {
                   if(cellById) {
                       let classDef = data.workflowNodes.find((s) => {return s._type_ === e._type_})
                       var oldCellEntity = cellById.attributes.entity
                       if(!this.updateCell(cellById, e, classDef)) {
                           this.needReuildGraph = true;
                       }
                       cells.forEach(c=>{
                           if(c.attributes.type === "devs.transformation.Flow"){
                               let t = c.attributes.entity;
                               if(t.start === oldCellEntity){
                                   t.start = e
                               }
                               if(t.finish === oldCellEntity){
                                   t.finish = e
                               }
                           }
                       })
                       console.log("conflict was resolved")
                   } else {
                       console.log("225 --------------------------------------------------------------")
                       this.needReuildGraph = true
                   }
               }
           })
       }
    }

}

export default translate()(WorkflowDesignerView);
