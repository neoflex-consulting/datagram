import React from 'react';
import { translate } from 'react-i18next'
import update from 'immutability-helper';
import DesignerView from './DesignerView.js'
import data from '../designer/designer.json';
import { DgPaper } from '../../joint/transformation/step.js';
import { Avatar, Tooltip } from 'antd';

class TransformationDesignerView extends DesignerView {

    constructor(...args) {
        super(...args);
        this.lineAgeComponent = {
            id: 'lineage',
            name: 'LineAge',
            component: 'TransformationDesignerView',
            menuItem: ()=>{return <span>
                        <Tooltip placement="bottom" title={this.props.t('lineage')}>
                            <Avatar src="images/icon-core/keep.svg" size={"small"}/>
                        </Tooltip>
                    </span>}
        }
        this.validationComponent = {
            id: 'validation',
            name: 'Validation',
            component: 'TransformationDesignerView',
            menuItem: ()=>{return <span>
                <Tooltip placement="bottom" title={this.props.t('validation')}>
                    <Avatar src="images/icon-core/check-modern.svg" size={"small"}/>
                </Tooltip>
            </span>}
        }
        this.runComponent = {
            id: 'run',
            name: 'Run',
            component: 'TransformationDesignerView',
            menuItem: ()=>{return <span>
                <Tooltip placement="bottom" title={this.props.t('run')}>
                    <Avatar src="images/icon-core/arrow-right-modern.svg" size={"small"}/>
                </Tooltip>
            </span>}
        }
        this.props.addBottomItem(this.lineAgeComponent, this.validationComponent, this.runComponent)
    }

    processLink(link, sourceCell, targetCell, sourceEntity, targetEntity, sourcePort, targetPort) {
        var entity = link.get("entity") || {_type_: "etl.Transition", name: link.id, sourcePort, targetPort }

        var idx = (this.props.entity["transitions"] || []).indexOf(entity)

        link.attributes.entity = entity

        if(idx !== -1){
            let n = update(this.props.entity, {
                    transitions: {
                        [idx]: {$merge: {sourcePort, targetPort}}
                    }
                }
            )
            link.attributes.entity = n.transitions[idx]
            this.props.updateEntity(n)
        } else {
            entity.start = sourcePort
            entity.finish = targetPort

            var newTransitions = update(this.props.entity["transitions"] || [], {$push: [entity]})
            var n = {}
            n["transitions"] = newTransitions
            link.attributes.entity = entity
            this.props.updateEntity(n)
        }
    }

    checkGraphConsistents() {
        if(!this.graph){
            return;
        }
        if(this.needReuildGraph === true) {
            return;
        }
        this.needReuildGraph = false
        var cells = this.graph.getCells()
        if(this.props.entity) {
            let t = this.props.entity
            const nodes = (t.sources || []).concat(t.targets || []).concat(t.transformationSteps || []).concat(t.transitions || [])
            if(nodes.length !== cells.length){
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
                       let classDef = data.transformationSteps.find((s) => {return s._type_ === e._type_})
                       if(!this.updateCell(cellById, e, classDef)) {
                           this.needReuildGraph = true;
                       }
                       console.log("conflict was resolved")
                   } else {
                       this.needReuildGraph = true
                   }
               }
            })
       }
    }

    findPortDefinition(port) {
        let t = this.props.entity
        let nodes = (t.sources || []).concat(t.targets || []).concat(t.transformationSteps || [])
        var res
        nodes.forEach(node=>{
            let def = data.transformationSteps.find(def=>def._type_ === node._type_)
            def.ports.forEach(portdef=>{
                let nodeport = node[portdef.attribute]
                if(nodeport === port || (port.e_id && (port.e_id === nodeport.e_id))) {
                    res = {
                        node: node,
                        port: nodeport,
                        def: portdef,
                        portId: portdef.id
                    }
                }
                if(Array.isArray(nodeport)) {
                    nodeport.forEach((p, index)=>{
                        if(p === port || (port.e_id && (port.e_id === p.e_id))) {
                            res = {
                                node: node,
                                port: p,
                                def: portdef,
                                portId: index.toString()
                            }
                        }
                    })
                }
            })
        })
        return res
    }

    deleteCashedLinks(updatedCells) {
        if(this.linksToDelete.length > 0) {
            var updatedLinks = this.props.entity["transitions"]
            this.linksToDelete.forEach(linkEntity=>{
                const index = updatedLinks.indexOf(linkEntity)
                updatedLinks = update(updatedLinks, {$splice: [[index, 1]]});
            })
            updatedCells.transitions = updatedLinks
        }
    }

    buildLinks() {
        (this.props.entity.transitions || []).forEach((t) => {
            if (t.start && t.finish) {
                const s = this.findPortDefinition(t.start)
                const f = this.findPortDefinition(t.finish)
                DgPaper.createLink(s.node, f.node, s.portId, f.portId, this.graph, t);
            }
        })
    }

    componentWillUnmount(){
        super.componentWillUnmount();
        this.props.removeBottomItem(this.lineAgeComponent, this.validationComponent, this.runComponent)
    }
}

export default translate()(TransformationDesignerView);
