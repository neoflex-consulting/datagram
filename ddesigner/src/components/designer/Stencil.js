import React, { Component } from 'react';
import { translate } from 'react-i18next'
import { Tooltip, Menu, Avatar } from 'antd';
import $ from "jquery";
import { DgPaper } from '../../joint/transformation/step.js';

//const drop = function(text) { alert(text)}

class Stencil extends Component {

    getShapeGroups(){
        var result = []
        this.props.nodesDefs.forEach((s)=>{
            if(!result.includes(s.group)){
                result.push(s.group)
            }
        })
        return result
    }

    render() {
        const {t} = this.props
        return(
            <Menu mode="inline" className="hide-when-popup"
            selectable={false}
            defaultOpenKeys={this.getShapeGroups()}>
            {this.props.nodesDefs &&
                this.getShapeGroups().map((group)=>{
                    return (<Menu.SubMenu
                      className="hide-when-popup"
                      title={<span><Tooltip placement="right" title={t(group, {ns: 'common'})}><Avatar src={"images/" + group + ".svg"} shape="square" size={"large"} />{this.props.collapsed ? "" : t(group, {ns: 'common'})}</Tooltip></span>} key={group}
                      onTitleClick={()=>{
                          if(this.props.collapsed) {
                              this.props.toggle()
                          }
                      }}>
                      {
                          this.props.nodesDefs.filter((s)=>{return s.group === group})
                          .map((step, index)=>{
                          return(
                              <Menu.Item key={index}>
                                <Tooltip placement="right" title={step.label}>
                                    <Avatar src={step.image} shape="square" size={"large"} className={"dropStart"} />
                                    <span>{step.label}</span>
                                </Tooltip>
                              </Menu.Item>)
                          })
                      }
                    </Menu.SubMenu>)
                })
            }
            </Menu>)
     }

     bindStartDragListener(){
         $('img').on( "dragstart", function(evt) {
             var imgurl = evt.originalEvent.currentTarget.currentSrc;
             DgPaper.draggedStep = this.props.nodesDefs.find((sd)=>{
                 return imgurl.indexOf(sd.image) > -1;
             })
         }.bind(this));
     }

     componentDidUpdate(){
         this.bindStartDragListener()
     }

     componentDidMount(){
         this.bindStartDragListener()
     }
}

export default translate()(Stencil);
