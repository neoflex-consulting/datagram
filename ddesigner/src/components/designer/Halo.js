import React, { Component } from 'react';
import { translate } from 'react-i18next'
import { Tooltip, Menu, Avatar } from 'antd';

class Halo extends Component {

    selecteCellMenuClick(item){
        if(this.props.selected) {
            if(item.item.props.action) {
                if(item.item.props.action.component) {
                    this.props.onEditAction(item.item.props.action, this.props.selected)
                }
            }
            if(item.key === 'delete') {
                this.props.deleteCell(this.props.selected)
            }
            if(item.key === 'duplicate') {
                this.props.duplicateCell(this.props.selected)
            }
        }
    }

    render() {
        return (
            <Menu mode="horizontal" theme="dark" className="ant-menu-short"
                style={
                        {
                            position: "absolute",
                            left: this.props.position.x,
                            top:  (this.props.position.y + ((this.props.position.height + 25) * this.props.position.scalefactor)),
                            zIndex: 10
                        }
                    }
                onClick={(item)=>{this.selecteCellMenuClick(item)}}
                selectable={false}>
                <Menu.Item key={'delete'} action={null}>
                    <Tooltip placement="bottom" title="Delete">
                        <Avatar src={"images/icon-core/cannel.svg"} size={"small"}/>
                    </Tooltip>
                </Menu.Item>
                <Menu.Item key={'duplicate'} action={null}>
                    <Tooltip placement="bottom" title="Duplicate">
                        <Avatar src={"images/icon-core/copy-paste.svg"} size={"small"}/>
                    </Tooltip>
                </Menu.Item>
                {this.props.cellDefinition && this.props.cellDefinition.actions &&
                    this.props.cellDefinition.actions.map((a) => {
                        return (
                        <Menu.Item key={a.id} action={a}>
                            <Tooltip placement="bottom" title={a.label}>
                                <Avatar src={a.icon} size={"small"}/>
                            </Tooltip>
                        </Menu.Item>)
                    })
                }
            </Menu>
            )
     }

}

export default translate()(Halo);
