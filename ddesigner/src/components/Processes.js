import React, { Component } from 'react';
import { List } from 'antd';
import { translate } from 'react-i18next'

class Processes extends Component {

  render() {
    //const {t} = this.props
    return(
        <List
          size="small"
          bordered
          dataSource={this.props.processes}
          renderItem={item => (<List.Item>{item}</List.Item>)}
        />)
    }
}

export default translate()(Processes);
