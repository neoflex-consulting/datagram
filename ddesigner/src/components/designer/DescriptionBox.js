import React, {Component, Fragment} from 'react'
import { Popover, Input } from 'antd'
import propTypes from 'prop-types'

class DescriptionBox extends Component{

    static propTypes = {
        description: propTypes.string,
        onChange: propTypes.func
    }

    static defaultProps = {
        description: undefined,
        onChange: ()=>{}
    }

    constructor(...args){
        super(...args);
        this.state = {
            editingDescription: false
        }
    }

    render(){
        const splitedString = this.props.description ? this.props.description.split('\n') : []
        const lines = splitedString.length
        const content = (this.state.editingDescription ?
            <Input.TextArea autoFocus
                key="textedit"
                style={{ resize: 'none' }}
                autosize={{ maxRows: lines <= 15 ? lines + 1.5 : 15 }}
                defaultValue={this.props.description}
                onBlur={(e) => {
                    const newDescription = e.target.value
                    if (newDescription !== this.props.description) {
                        this.props.onChange(newDescription, e)
                    }
                    this.setState({ editingDescription: false })
                }}
            />
            :
            <Input.TextArea readOnly
                key="textview" 
                autosize={{ maxRows: lines <= 15 ? lines + 1.5 : 15 }}
                value={this.props.description} 
                style={{
                    border: 'none',
                    whiteSpace: 'pre',
                    overflow: 'auto',
                    resize: 'none'
                }}
                onClick={() => this.setState({ editingDescription: true })}/>)
        return(
            <Fragment>
                <Popover trigger="click" overlayStyle={{ minWidth: this.props.description && this.props.description.length > 15 ? '30%' :'17%' }} content={content}>
                    {this.props.children}
                </Popover>
            </Fragment> 
        )
    }

}

export default DescriptionBox