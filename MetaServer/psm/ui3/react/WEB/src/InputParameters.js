import 'bootstrap/dist/css/bootstrap.css'
import 'bootstrap/dist/css/bootstrap-theme.css'
import React, { Component } from 'react';
import {Modal, Button, FormGroup, ControlLabel, FormControl, Glyphicon, OverlayTrigger, Tooltip} from 'react-bootstrap'
import PropTypes from 'prop-types'
import { translate } from 'react-i18next'
import _ from 'lodash'

class InputParameters extends Component {
    static propTypes = {
        className: PropTypes.string.isRequired,
        opName: PropTypes.string.isRequired,
        showDialog: PropTypes.bool.isRequired,
        params: PropTypes.array.isRequired,
        onSubmit: PropTypes.func.isRequired,
        onCancel: PropTypes.func.isRequired
    }
    constructor(props) {
        super(props)
        this.state = {
            data: props.params.reduce((map, el)=>{
                map[el.name] = el.value
                return map
            }, {}),
            fileNames: {}
        }
    }
    render() {
        const {t, params, onSubmit, onCancel, className, opName, showDialog} = this.props
        return (
            <Modal show={showDialog} onHide={()=>onCancel()}>
                <form onSubmit={(e) => {
                    e.preventDefault()
                }}>
                    <Modal.Header closeButton>
                        <Modal.Title>{t('parameters')}</Modal.Title>
                    </Modal.Header>
                    <Modal.Body>
                        {params.map(((el, i)=>(
                        	(el.type === 'file' &&
                            <FormGroup>
                            <OverlayTrigger placement="top" overlay={<Tooltip id="upload">Upload file</Tooltip>}>
                                <label className="btn btn-primary btn-file btn-sm">
                                	{t(`${className}.ops.${opName}.params.${el.name}.caption`, {ns: 'classes'})}
                                    <Glyphicon glyph="upload"/>
                                    <input
                                        style={{display: "none"}}
                                        type="file"
                                        onClick={e=>{this.setState(_.merge(this.state, {
                                        	fileNames: {[el.name]: ''}                                        	
                                        }))}}
                                        onChange={e=>{this.setState(_.merge(this.state, {
                                        	data: {[el.name]: e.target.files[0]},
                                        	fileNames: {[el.name]: e.target.files[0].name.replace(/\\/g, '/').replace(/.*\//, '')}                                        	
                                        }))}}
                                        />
                                </label>
                                </OverlayTrigger>
                                <FormControl
                                	autoFocus={i === 0}
                                    type="text"
                                    value={this.state.fileNames[el.name]}
                                    readOnly="true"/>
                            </FormGroup>)
                            || <FormGroup key={el.name} controlId={el.name}>
                                <ControlLabel>{t(`${className}.ops.${opName}.params.${el.name}.caption`, {ns: 'classes'})}</ControlLabel>
	                                <FormControl
	                                autoFocus={i === 0}
	                                type={el.type}
	                                value={this.state.data[el.name]}
	                                onChange={e=>{this.setState(_.merge(this.state, {data: {[el.name]: e.target.value}}))}}
	                            	/>
                            </FormGroup>
                        )))}
                    </Modal.Body>
                    <Modal.Footer>
                        <Button bsStyle="primary" type="submit" onClick={()=>{
                            onSubmit(this.state.data)
                        }}>{t('execute')}</Button>
                    </Modal.Footer>
                </form>
            </Modal>
        )
    }
}

export default translate()(InputParameters)
