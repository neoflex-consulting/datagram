import joint from 'jointjs/dist/joint.js';
import { classExtension } from '../../components/classExtension.js';
import $ from "jquery";
import {instantiate} from '../../utils/meta'

var elements = []

const linkColor = '#4d4d4dde'
const inportColor = 'white'
const inportStroke = '#4d4d4dde'
const outStroke = '#4d4d4dde'
const outportColor = '#4d4d4dde'
const circleR = 7;
const circleSW = 1;
const padding = 20
var sourceMarkup = `<rect class="resizer"/>
                    <rect class="border"/>
                    <g class="rotatable">
                        <g class="scalable">
                            <image preserveAspectRatio="none" class="body"/>
                        </g>
                        <text class="label"/>
                    </g>`;
var sourceTypeClass = {
        size: {width: 80, height: 80 },
        attrs: {
            stroke: "green",
            title: {text: 'SQL source ss'},
            '.body': {width: 40, height: 40},
            '.border': {'ref-width': 1, 'ref-height': 1, ref: '.body', stroke: 'navy', 'stroke-dasharray': 5, style: 'visibility: hidden;'},
            '.resizer': {ref: '.body', 'ref-dx': 0, 'ref-dy': 0, width: 5, height: 5, cursor: 'se-resize', stroke: 'navy', style: 'visibility: hidden;'},
            '.label': { text: 'SQL source', fill: '#000000', ref: '.body', refX: '0%', 'ref-dy': 2, 'font-size': 10, stroke: '#000000', 'stroke-width': 0 },
             image: {}
        }
    };
var DgPaper = {
    defineStep: function(type, label, image, ports) {
        var localSourceConfig = JSON.parse(JSON.stringify(sourceTypeClass));

        localSourceConfig.type = type;
        localSourceConfig.attrs.image['xlink:href'] = image;
        localSourceConfig.attrs['.label'].text = label;
        localSourceConfig.inPorts = ports ? ports.filter((p)=>{ return  p.group === "in" }).map((p) => {return p.id}) : []
        localSourceConfig.outPorts = ports ? ports.filter((p)=>{ return  p.group === "out" }).map((p) => {return p.id}) : []
        localSourceConfig.ports = {
            groups: {
                'in': {
                    position: {name: 'left'},
                    attrs: {
                        '.port-body': {
                            fill: inportColor,
                            magnet: 'passive'
                        },
                        '.port-label': {
                            text: "",
                            visibility: 'hidden'
                        },
                        circle: {
                            r: circleR,
                            stroke: inportStroke,
                            'stroke-width': circleSW
                        }
                    }
                },
                'out': {
                    position: {name: 'right'},
                    label: { position: {name: 'right'} },
                    attrs: {
                        '.port-body': {
                            fill: outportColor
                        },
                        '.port-label': {
                            text: "",
                            visibility: 'hidden'
                        },
                        circle: {
                            r: circleR,
                            stroke: outStroke,
                            'stroke-width': circleSW
                        }
                    }
                    }
                }
        }

        var element = joint.shapes.devs.Model.define(
            type,
            localSourceConfig,
            {
                markup: sourceMarkup,
                initialize: function() {
                    joint.shapes.devs.Model.prototype.initialize.apply(this, arguments);
                }
            }
        )

        elements.push({type: type, element: element})
    },

    Link: null,
    defineLink: function(linkDeleted){
        this.Link = joint.dia.Link.extend({
                defaults: joint.util.deepSupplement({
                    type: 'devs.transformation.Flow',
                    attrs: {'.': { magnet: false }, '.marker-target': { d: 'M 10 0 L 0 5 L 10 10 z' }, path: {fill: linkColor, stroke: linkColor}}
                    }
                ),
                remove: function(){
                    joint.dia.Link.prototype.remove.apply(this, arguments);
                    if(linkDeleted){
                        linkDeleted(this)
                    }
                }
            })
    },
    updatePorts: function(cell, typePorts, entity) {
        var success = true;
        (typePorts || []).filter(port=>port.multiple).forEach((port)=>{
            if(port.attribute){
                let entityPorts = (entity[port.attribute] || [])
                let cellPorts = (cell.attributes.ports.items || []).filter(p => p.group === port.group && p.id !== "addNew")
                if(entityPorts.length !== cellPorts.length) {
                    success = false
                }
                if(success === true){
                    (entity[port.attribute] || []).forEach((entityPort, index) => {
                        let cellPort = cell.attributes.ports.items.find(p=>p.group === port.group && p.id === index.toString())
                        cell.portProp(cellPort.id, 'attrs/.port-label/text', entityPort[port.labelAttribute]);
                    });
                }
            }
        });
        return success
    },
    processPorts: function(cell, typePorts, entity) {
        (typePorts || []).filter(port=>port.multiple).forEach((port)=>{
            if(port.attribute){
                cell.getPorts().filter(p=>p.multiple).forEach(p=>cell.removePort(p))
                cell.removePort(port.id);
                (entity[port.attribute] || []).forEach((entityPort, index) => {
                    cell.addPort({
                        id: index.toString(),
                        multiple: true,
                        group: port.group,
                        attrs: {
                            '.port-label': {
                                text: entityPort[port.labelAttribute],
                                visibility: 'visible'
                            },
                            circle: {
                                fill: port.group === "in" ? inportColor : outportColor,
                                r: circleR,
                                stroke: port.group === "in" ? inportStroke : outStroke,
                                'stroke-width': circleSW
                            }
                        }
                    });
                });
            }
            if(port.group === "out") {
                cell.addPort({
                    id: "addNew",
                    portDef: port,
                    multiple: true,
                    group: port.group,
                    attrs: {
                        '.port-label': {
                            text: "+",
                            visibility: 'visible'
                        },
                        circle: {
                            fill: port.group === "in" ? inportColor : outportColor,
                            r: circleR,
                            stroke: port.group === "in" ? inportStroke : outStroke,
                            'stroke-width': circleSW
                        }
                    }
                });
            }
        });
    },
    createCell: (celltype, x, y, graph, entity, width, height, noadd) => {
        const c = elements.find((e)=>{
            return e.type === celltype.type
        })
        var cell = (new c.element()).position(x, y)
        if(width > 0 && height > 0){
            cell = cell.size(width, height)
        }
        if(noadd !== true) {
            cell.addTo(graph);
        }

        cell.attr(".label/text", entity.label || entity.name);
        cell.set("name", entity.name)
        cell.set("entity", entity)

        DgPaper.processPorts(cell, celltype.ports, entity)
        return cell
    },

    createLink: function(sourceEntity, targetEntity, sourcePort, targetPort, graph, transition, noadd){
        var sourceCell = graph.get("cells").models.find((m)=>{return m.get("entity") === sourceEntity});
        var targetCell = graph.get("cells").models.find((m)=>{return m.get("entity") === targetEntity});
        if(sourceCell && targetCell) {
            var newlink = new this.Link({
                    source: { id: sourceCell.id, port: sourcePort},
                    target: { id: targetCell.id, port: targetPort}
                })
            if(noadd !== true){
                newlink.addTo(graph);
            }
            newlink.set("entity", transition)
            return newlink;
        }
    },

    Paper: joint.dia.Paper.extend({
        events: {
            'mousedown': 'pointerdown',
            'dblclick': 'mousedblclick',
            'click': 'mouseclick',
            'touchstart': 'pointerdown',
            'touchend': 'mouseclick',
            'touchmove': 'pointermove',
            'mousemove': 'pointermove',
            'mouseover .joint-cell': 'cellMouseover',
            'mouseout .joint-cell': 'cellMouseout',
            'contextmenu': 'contextmenu',
            'mousewheel': 'mousewheel',
            'DOMMouseScroll': 'mousewheel',
            'mouseenter .joint-cell': 'cellMouseenter',
            'mouseleave .joint-cell': 'cellMouseleave',
            'mousedown .joint-cell [event]': 'cellEvent',
            'touchstart .joint-cell [event]': 'cellEvent',
            'dragenter' : 'highlightDropZone',
            'dragleave' : 'unhighlightDropZone',
            'drop' : 'drop',
            'resize': 'resize',
            'dragover': function(ev) {
                ev.preventDefault();
            }
        },
        drop: function(evt)
        {
            console.log(evt)
        }
    }),
    draggedStep: undefined,
    buildGraph: function(cellCreated, linkConnected, selectCellView, cellDblClick, cellMove) {
        var result = {}

        result.graph = new joint.dia.Graph();
        var Paper = this.Paper.extend({
            drop: (evt) => {
                if(this.draggedStep){
                    var newEntity = instantiate(this.draggedStep._type_)
                    var config = classExtension.all().find(n=>n._type_ === newEntity._type_)

                    newEntity.name = config.label.replace(" ", "_") + "_" + result.graph.getCells().length
                    newEntity.label = newEntity.name

                    config.ports.filter(p=>p._type_).forEach(port=>{
                        newEntity[port.attribute] = port.multiple ? [] : instantiate(port._type_, {name: newEntity.name + port.id})
                    })
                    let ox = result.paper._viewportMatrix ? result.paper._viewportMatrix.e : 0
                    let oy = result.paper._viewportMatrix ? result.paper._viewportMatrix.f : 0
                    let sx = result.paper._viewportMatrix ? result.paper._viewportMatrix.a : 1
                    let sy = result.paper._viewportMatrix ? result.paper._viewportMatrix.d : 1
                    const cell = this.createCell(this.draggedStep, ((evt.offsetX - ox) / sx) - 35, ((evt.offsetY - oy)/ sy) - 35, result.graph, newEntity)

                    cellCreated(cell)
                }
                evt.preventDefault()
            }
        })
        result.paper = new Paper({
            el: document.getElementById("paperContainer"),
            width: 2000,
            height: 2000,
            gridSize: 30,
            drawGrid: { name: 'dot', args: { color: '#e4e4e4', thickness: 3 }},
            model: result.graph,
            perpendicularLinks: true,
            linkPinning: false,
            markAvailable: true,
            defaultLink: new this.Link(),
            validateConnection: function(cellViewS, magnetS, cellViewT, magnetT, end, linkView) {
                if (magnetS && magnetS.getAttribute('port-group') === 'in') return false;
                if (cellViewS === cellViewT) return false;
                return magnetT && magnetT.getAttribute('port-group') === 'in';
            },
            validateMagnet: function(cellView, magnet) {
                return magnet.getAttribute('magnet') !== 'passive';
            },
            preventContextMenu: false,
            clickThreshold: 1
        });
        result.paper.on('cell:pointerdown', (cellView, evt, x, y) => {
            if(evt.target.attributes.cursor && evt.target.attributes.cursor.value === 'se-resize') {
                cellView.paper.options.interactive.elementMove = false;
            }
        })
        result.paper.on('cell:pointerup', (cellView, evt, x, y) => {
            cellView.paper.options.interactive.elementMove = true;
        })
        result.paper.on('link:connect', (linkView, evt, elementViewConnected, magnet, arrowhead) => {
            linkConnected(linkView.model)
        });
        result.paper.on('cell:pointerclick', (cellView) => {
            selectCellView(cellView)
        });
        result.paper.on('blank:pointerdown', () => {
            selectCellView(null)
            DgPaper.inmove = true
            document.body.style.cursor = "move"
        });
        result.paper.on('blank:pointerup', () => {
            DgPaper.inmove = false
             document.body.style.cursor = "auto"
             DgPaper._clientX = 0;
             DgPaper._clientY = 0;
        });
        result.paper.on('cell:pointerdblclick', (cellView, evt, x, y) => {
            cellDblClick(cellView, evt, x, y)
        });
        result.paper.on('cell:pointermove', (cellView, evt, x, y) => {
            cellMove(cellView, x, y)
        });
        result.paper.on('element:pointermove', (cellView, evt, x, y) => {
            if(cellView.paper.options.interactive.elementMove === false) {
                let newWidth = cellView.model.attributes.size.width + evt.originalEvent.movementX
                let newHeight = cellView.model.attributes.size.height + evt.originalEvent.movementY
                if(newWidth > 50 && newHeight > 60){
                    cellView.model.resize(newWidth, newHeight)
                    cellMove(cellView, x, y)
                }
            }
        });
        result.paper.on('link:disconnect', (linkView, evt, elementViewDisconnected, magnet, arrowhead) => {
            console.log('link:disconnect')
        });

        return result;
    },
    rearrange: function(graph) {
        var pad = padding; // padding for the very left and very top element.
        joint.layout.DirectedGraph.layout(graph, {
            setLinkVertices: false,
            rankDir: 'LR',
            setPosition: function(cell, box) {
                cell.position(box.x - box.width / 2 + pad, box.y - box.height / 2 + pad);
            }
        });
    }
}

$(document.body).on('mousemove', (evt)=>{
    if(DgPaper.inmove) {

        evt = joint.util.normalizeEvent(evt);

        var container = $('#paperContainer').parent()

        var dx = evt.clientX - (DgPaper._clientX ? DgPaper._clientX : evt.clientX);
        var dy = evt.clientY - (DgPaper._clientY ? DgPaper._clientY : evt.clientY);

        if(dy !== 0){
            container.scrollTop(container.scrollTop() - dy);
        }
        if(dx !== 0) {
            container.scrollLeft(container.scrollLeft() - dx);
        }

        DgPaper._clientX = evt.clientX;
        DgPaper._clientY = evt.clientY;
    }
})

export { DgPaper }
